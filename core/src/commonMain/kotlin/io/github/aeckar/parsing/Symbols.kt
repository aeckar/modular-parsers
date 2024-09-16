package io.github.aeckar.parsing

import io.github.aeckar.parsing.containers.CharRevertibleIterator
import io.github.aeckar.parsing.typesafe.*
import io.github.aeckar.parsing.utils.*

private inline fun Symbol.parenthesizeIf(predicate: (Symbol) -> Boolean): String {
    return if (predicate(this)) "($this)" else toString()
}

/**
 * Returns the concatenation of the substrings of all elements in this list.
 */
private fun List<SyntaxTreeNode<*>>.concatenate() = joinToString("") { it.substring }

// ------------------------------ generic symbols ------------------------------

/**
 * Used to parse a specific kind of token in a given input.
 *
 * The building block of a parser rule.
 */
public abstract class Symbol internal constructor(
) : ParserComponent() { // Unseal to allow inheritance from type-safe symbols
    protected fun debugUnwrap(wrapped: Symbol) {
        debug { "Unwrapping symbol: ${wrapped.toString().blue()}" }
    }

    protected fun debugQuery(query: Symbol) {
        debug { "Attempting match to: ${query.toString().blue()}" }
    }

    protected fun debugMatchSuccess(result: SyntaxTreeNode<*>) {
        debug {
            "Match succeeded".greenEmphasis() +
            " (substring = '" + result.substring.withEscapes().yellow() + "')"
        }
    }

    protected fun debugMatchSuccess(result: SyntaxTreeNode<*>, lazyReason: () -> String) {
        debug {
            "Match succeeded".greenEmphasis() +
            " (${lazyReason()}, substring = '" + result.substring.withEscapes().yellow() + "')"
        }
    }

    protected fun debugMatchFail() {
        debug { "Match failed".redEmphasis() }
    }

    protected fun debugMatchFail(lazyReason: () -> String ) {
        debug { "Match failed".redEmphasis() + " (${lazyReason()})" }
    }

    /**
     * Returns null if this symbol is in the top of the fail stack, and logs the failure.
     * Otherwise, performs the following:
     * 1. Saves the current input position
     * 2. Logs whether the symbol was matched or not
     * 3. Appends this symbol to the top of the fail stack if not matched
     * 4. Returns the result
     *
     * Either this function, or [matchNoCache] must be overridden, but not both.
     */
    internal abstract fun match(attempt: ParsingAttempt): SyntaxTreeNode<*>?

    /**
     * Attempts to match this symbol to the current position in the input without performing caching.
     *
     * Invoked by the default implementation of [match], which handles fail/success caching.
     * @throws UnsupportedOperationException this is a [NamedSymbol] or [End] symbol
     */
    internal abstract fun matchNoCache(attempt: ParsingAttempt): SyntaxTreeNode<*>?
}

/**
 * A symbol that can be given a name by delegating it to a property.
 *
 * Delegating an instance of this class to a property produces a [NamedSymbol].
 *
 * Doing so enables:
 * - [Importing][ParserDefinition.import] of symbols from other parsers
 * - Definition of [recursive][TypeSafeSymbol] symbols
 */
public abstract class NameableSymbol<Self : NameableSymbol<Self>> internal constructor() : Symbol() {
    private val lazyString: String by lazy { resolveString() }

    /**
     * Performs the following:
     * 1. Consumes the next substring matching the skip symbol, if not null
     * 2. Pushes an empty list to the [fail stack][InputPosition.fails]
     */
    internal fun align(attempt: ParsingAttempt) {
        attempt.skip?.let {
            debug { "Attempting match to skip: ".magentaBold() + attempt.skip.toString().blue() }
            val previousSkip = attempt.skip
            attempt.skip = null // Prevent infinite recursion
            it.match(attempt)
            attempt.skip = previousSkip
            debug { "End skip".magentaBold() }
        }
    }

    internal abstract fun resolveString(): String

    override fun unwrap() = this as Symbol

    override fun match(attempt: ParsingAttempt): SyntaxTreeNode<*>? {
        val startPos = attempt.input.here()
        if (this in startPos.fails) {
            debugMatchFail { "Previous attempt failed" }
            return null
        }
        startPos.successes[this]?.let {
            debugMatchSuccess(it) { "Previous attempt succeeded" }
            return it.unsafeCast()
        }
        debug { println(attempt.input.pivots()); "Attempting match" }
        startPos.symbols += this
        attempt.input.save()
        val result = matchNoCache(attempt)
        if (result != null) {
            debugMatchSuccess(result)
            startPos.successes[this] = result
        } else {
            debugMatchFail()
            startPos.fails += this
        }
        return result
    }

    final override fun toString(): String = lazyString
}

/**
 * A symbol given a name by being delegated to a property.
 */
public open class NamedSymbol<UnnamedT : NameableSymbol<out UnnamedT>> internal constructor(
    override val name: String,
    internal var unnamed: NameableSymbol<out UnnamedT>
) : Symbol(), Named {

    final override fun unwrap() = unnamed

    override fun match(attempt: ParsingAttempt): SyntaxTreeNode<*>? {
        debugUnwrap(unnamed)
        return unnamed.match(attempt)?.also { it.unsafeCast<SyntaxTreeNode<Symbol>>().source = this }
    }

    final override fun matchNoCache(attempt: ParsingAttempt): SyntaxTreeNode<*>? {
        return match(attempt)
    }

    final override fun toString(): String = name
}

/**
 * A symbol [imported][ParserDefinition.import] from another parser.
 */
public class ForeignSymbol<UnnamedT : NameableSymbol<out UnnamedT>>(
    named: NamedSymbol<out UnnamedT>,
    internal val origin: Parser
) : NamedSymbol<UnnamedT>(named.name, named.unnamed) {
    override fun match(attempt: ParsingAttempt): SyntaxTreeNode<*>? {
        debugUnwrap(unnamed)
        val previousSkip = attempt.skip
        attempt.skip = (origin as? LexerlessParser)?.resolveSkip()
        val result = unnamed.match(attempt)
        attempt.skip = previousSkip
        return result
    }
}

/**
 * A symbol representing a symbol that is not a [TypeUnsafeSymbol].
 */
public sealed class SimpleSymbol<Self : SimpleSymbol<Self>> : NameableSymbol<Self>()

/**
 * A symbol comprised of more than one other symbol.
 *
 * Can only be named by wrapping this instance in a typed subclass (`<subclass>2`, `<subclass>3`, ...).
 */
public sealed class TypeUnsafeSymbol<
    TypeSafeT : TypeSafeSymbol<*, *>,
    Self : TypeUnsafeSymbol<TypeSafeT, Self>
> : NameableSymbol<Self>() {
    internal val components: MutableList<Symbol> = mutableListOf()

    final override fun unwrap() = super.unwrap()

    // Will not be called before all components are assembled
    abstract override fun resolveString(): String
}

/**
 * A symbol used to produce tokens during lexical analysis.
 *
 * Ensures separation between parser symbols literals.
 */
public class LexerSymbol internal constructor(
    private val start: Fragment,
    private val behavior: Behavior = Behavior.DO_NOTHING
) : NameableSymbol<LexerSymbol>(), LexerComponent {
    /**
     * Can be combined with other fragments to create a named [LexerSymbol].
     */
    public class Fragment internal constructor(
        internal val root: Symbol
    ) : ParserComponent(), LexerComponent {
        internal fun lex(data: ParsingAttempt) = root.match(data)?.substring

        override fun unwrap() = root.unwrap()
        override fun toString(): String = root.toString()
    }

    /**
     * Designates a [Behavior] to a [Fragment].
     *
     * Can be delegated to a property to create a named [LexerSymbol].
     */
    public class Descriptor internal constructor(
        internal val fragment: Fragment,
        internal val behavior: Behavior
    )

    /**
     * An action that a [LexerSymbol] performs on the current stack of lexer modes.
     */
    public class Behavior internal constructor(
        internal val action: MutableList<String>.() -> Unit
    ) {
        internal companion object {
            val DO_NOTHING = Behavior {}
        }
    }

    override fun matchNoCache(attempt: ParsingAttempt): SyntaxTreeNode<*>? {
        debugUnwrap(start.root)
        return start.lex(attempt)?.let {
            attempt.modeStack.apply(behavior.action)
            SyntaxTreeNode(this, it)
        }
    }

    override fun unwrap() = start.unwrap()
    override fun resolveString() = start.toString()
}

// ------------------------------ specialized symbols ------------------------------

/**
 * A symbol matching a string of characters.
 */
public class Text internal constructor(private val literal: String) : SimpleSymbol<Text>() {
    internal constructor(query: Char) : this(query.toString())

    override fun matchNoCache(attempt: ParsingAttempt): SyntaxTreeNode<*>? {
        val input = attempt.input
        val matchExists = if (input is CharRevertibleIterator<*>) {
            literal.all { input.hasNext() && it == input.next() }
        } else if (input.isExhausted()) {
            false
        } else {
            toString() == (input.next() as Token).name
        }
        input.revert()
        if (!matchExists) {
            return null
        }
        input.advance(if (input is CharRevertibleIterator<*>) literal.length else 1)
        return SyntaxTreeNode(this, literal)
    }

    override fun resolveString() = "\"${literal.withEscapes()}\""
}

/**
 * A symbol matching a single character agreeing with a set of ranges and exact characters.
 */
public class Switch internal constructor(
    private val literal: String,    // Before optimization
    private val ranges: List<CharRange>
) : SimpleSymbol<Switch>() {
    override fun matchNoCache(attempt: ParsingAttempt): SyntaxTreeNode<*>? {
        val input = attempt.input
        var substring = ""
        val matchExists = if (input.isExhausted()) {
            false
        } else if (input is CharRevertibleIterator<*>) {
            val next = input.peek()
            substring = next.toString()
            next satisfies ranges
        } else {
            val next = input.peek() as Token
            substring = next.substring
            toString() != next.name // Faster than checking for satisfaction
        }
        input.revert()
        if (!matchExists) {
            return null
        }
        input.advance(1)
        return SyntaxTreeNode(this, substring)
    }

    override fun resolveString() = "[${literal.withEscapes()}]"

    public companion object {
        /**
         * Matches any single character.
         */
        public val ANY_CHAR: Switch = Switch("-", listOf(Char.MIN_VALUE..Char.MAX_VALUE))
    }
}

// TODO add "begin" and "end" messages for parsers during lexing, parsing
/**
 * A symbol matching another symbol one or more times in a row.
 *
 * If any one match is of an empty substring, no more matches are attempted.
 */
public class Repetition<SubMatchT : Symbol>(private val query: SubMatchT) : SimpleSymbol<Repetition<SubMatchT>>() {
    override fun matchNoCache(attempt: ParsingAttempt): SyntaxTreeNode<*>? {
        val input = attempt.input
        var subMatch: SyntaxTreeNode<SubMatchT>? = query.match(attempt).unsafeCast()
        val subMatches = mutableListOf<SyntaxTreeNode<SubMatchT>>()
        while (subMatch != null) {
            subMatches += subMatch
            if (subMatch.substring.isEmpty()) { // No need to push to fail stack
                debug { "End matches to query (matched substring is empty)" }
                break
            }
            align(attempt)
            subMatch = query.match(attempt).unsafeCast()
        }
        debug { "Query matched ${subMatches.size} times" }
        input.revert()
        if (subMatches.isEmpty()) {
            return null
        }
        val result = RepetitionNode(this, subMatches.concatenate(), subMatches)
        input.advance(if (input is CharRevertibleIterator<*>) result.substring.length else result.branches.size)
        return result
    }

    override fun resolveString() = query.parenthesizeIf { it is TypeSafeSymbol<*, *> } + "+"
}

/**
 * A symbol matching another symbol, or a zero-length token if that symbol is not found.
 */
public class Option<SubMatchT : Symbol>(private val query: SubMatchT) : SimpleSymbol<Option<SubMatchT>>() {
    private val emptyMatch = OptionNode(this, "", null)

    override fun matchNoCache(attempt: ParsingAttempt): SyntaxTreeNode<*> {
        return query.match(attempt)?.let { OptionNode(this, it.substring, it.unsafeCast()) } ?: emptyMatch
    }

    override fun resolveString() = query.parenthesizeIf { it is TypeSafeSymbol<*, *> } + "?"
}

/**
 * A symbol matching the first of any but one named symbol in the parser this symbol originates from.
 *
 * If the affiliated parser is a [NameableLexerParser], match attempts are only made to lexer symbols.
 */
public class Inversion(
    private val antiquery: NamedSymbol<*>
) : SimpleSymbol<Inversion>() {
    internal var origin: Parser by OnceAssignable(raise = ::IllegalStateException)

    override fun matchNoCache(attempt: ParsingAttempt): SyntaxTreeNode<*>? {
        debug { "Attempting match to any not of: " + antiquery.toString().blue() }
        return origin.resolveSymbols().values
            .asSequence()
            .mapNotNull {
                debugQuery(it)
                it.match(attempt)
            }
            .firstOrNull()
    }

    override fun resolveString() = "!${antiquery.parenthesizeIf { it !is SimpleSymbol<*> }}"
}

/**
 * A symbol matching one of several possible other symbols.
 */
public class TypeUnsafeJunction<TypeSafeT : TypeSafeJunction<TypeSafeT>> internal constructor(
) : TypeUnsafeSymbol<TypeSafeT, TypeUnsafeJunction<TypeSafeT>>() {
    internal lateinit var typeSafe: TypeSafeJunction<*>

    internal constructor(option1: Symbol, option2: Symbol) : this() {
        components += option1
        components += option2
    }

    override fun matchNoCache(attempt: ParsingAttempt): SyntaxTreeNode<*>? {
        val startPos = attempt.input.here()
        val result = components.asSequence()
            .filter { it !in startPos.symbols /* prevent infinite recursion */ }
            .map {
                debugQuery(it)
                it.match(attempt)
            }
            .withIndex()
            .find { it.value != null }
            ?.unsafeCast<IndexedValue<SyntaxTreeNode<*>>>()
            ?.let { JunctionNode(typeSafe, it.value.substring, it.value, it.index) }
        attempt.input.removeSave()
        return result
    }

    override fun resolveString() = components.joinToString(" | ")
}

/**
 * A symbol matching multiple symbols in a certain order.
 */
public class TypeUnsafeSequence<TypeSafeT : TypeSafeSequence<TypeSafeT>> internal constructor(
) : TypeUnsafeSymbol<TypeSafeT, TypeUnsafeSequence<TypeSafeT>>() {
    internal lateinit var typeSafe: TypeSafeSequence<*>

    internal constructor(query1: Symbol, query2: Symbol) : this() {
        components += query1
        components += query2
    }

    override fun matchNoCache(attempt: ParsingAttempt): SyntaxTreeNode<*>? {
        val input = attempt.input
        val firstQuery = components.first()
        if (firstQuery !is TypeUnsafeJunction<*> && firstQuery in attempt.input.here().symbols) {
            // Prevent infinite recursion. Does not apply to junctions since they already handle infinite recursion
            debug { "Left-recursion found for non-junction query: $firstQuery" }
            input.removeSave()
            return null
        }
        // No need to clarify that each subsequent match is a query in log, since they are ordered
        var subMatch: SyntaxTreeNode<*>?
        val subMatches = mutableListOf<SyntaxTreeNode<*>>()
        for (query in components) {
            subMatch = query.match(attempt)
            if (subMatch == null) {
                input.revert()
                return null
            }
            subMatches.add(subMatch)
            if (subMatch.substring.isNotEmpty()) {
                align(attempt)
            }
        }
        input.removeSave()
        return SequenceNode(typeSafe, subMatches.concatenate(), subMatches)
    }

    override fun resolveString() = components.joinToString(" ") { component ->
        component.parenthesizeIf { it is TypeSafeJunction<*> }
    }

    public companion object {
        /**
         * Matches all characters up to, and including, a newline (`'\n'`) character.
         */
        public val LINE: TypeUnsafeSequence<*> = TypeUnsafeSequence().apply {
            val ranges = listOf(Char.MIN_VALUE..'\u0009', '\u000b'..Char.MAX_VALUE)
            components += Repetition(Switch("-\u0009\u000b-", ranges))
            components += Text("\n")
        }
    }
}

/**
 * A symbol matching the end of some input.
 */
public class End : SimpleSymbol<End>() {  // Doesn't make sense to cache this
    override fun match(attempt: ParsingAttempt): SyntaxTreeNode<*>? {
        debug { "Matching to end of input" }
        return if (attempt.input.isExhausted()) {
            SyntaxTreeNode(this, "").apply(::debugMatchSuccess)
        } else {
            debugMatchFail()
            null
        }
    }

    override fun matchNoCache(attempt: ParsingAttempt) = match(attempt)
    override fun resolveString() = "<end of input>"
}