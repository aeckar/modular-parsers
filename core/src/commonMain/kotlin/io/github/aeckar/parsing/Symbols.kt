package io.github.aeckar.parsing

import io.github.aeckar.parsing.ParsingAttempt.Pivot
import io.github.aeckar.parsing.primitives.CharRevertibleIterator
import io.github.aeckar.parsing.typesafe.*
import io.github.aeckar.parsing.utils.*

private inline fun <reified T> Symbol.parenthesizeIf() = if (this is T) "($this)" else toString()

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
public abstract class Symbol internal constructor() : ParserComponent() {
    /**
     * Returns null if this symbol is in the top of the fail stack, and logs the failure.
     * Otherwise, performs the following:
     * 1. Saves the current input position
     * 2. Logs whether the symbol was matched or not
     * 3. Appends this symbol to the top of the fail stack if not matched
     * 4. Returns the result
     */
    internal inline fun <MatchT : SyntaxTreeNode<*>?> matching(
        attempt: ParsingAttempt,
        crossinline block: () -> MatchT
    ) = with(attempt) {
        val symbol = this@Symbol
        if (symbol in here().fails) {
            symbol.debugMatchFail { "Previous match attempt failed" }
            return@with null
        }
        here().successes[symbol]?.let {
            symbol.debugMatchSuccess(it)
            return@with it
        }
        symbol.debug { "Attempting match" }
        here().symbols += symbol // Including symbol wrappers
        input.save()
        val result = block()
        if (result != null) {
            symbol.debugMatchSuccess(result)
            here().successes[symbol] = result
            if (result.substring.isNotEmpty()) {
                pivotStack += Pivot()
            }
        } else {
            symbol.debugMatchFail()
            here().fails += symbol
        }
        result
    }

    /**
     * Performs the following:
     * 1. Consumes the next substring matching the skip symbol, if not null
     * 2. Pushes an empty list to the [fail stack][ParsingAttempt.Pivot.fails]
     */
    internal fun align(attempt: ParsingAttempt) {
        attempt.skip?.let {
            debug { "Attempting match to skip ${attempt.skip}" }
            val previousSkip = attempt.skip
            attempt.skip = null // Prevent infinite recursion
            it.match(attempt)
            attempt.skip = previousSkip
            debug { "End skip" }
        }
    }

    internal abstract fun match(attempt: ParsingAttempt): SyntaxTreeNode<*>?
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
    final override val rawName: String by lazy { resolveRawName() }

    /**
     * The returned name is not enclosed in parentheses.
     */
    internal abstract fun resolveRawName(): String
}

/**
 * A symbol given a name by being delegated to a property.
 */
public open class NamedSymbol<UnnamedT : NameableSymbol<out UnnamedT>> internal constructor(
    override val name: String,
    internal var unnamed: NameableSymbol<out UnnamedT>
) : Symbol(), Named {
    final override val rawName: String get() = name

    final override fun unwrap() = unnamed

    override fun match(attempt: ParsingAttempt) = matching(attempt) {
        val result = unnamed.match(attempt)?.also { it.unsafeCast<SyntaxTreeNode<Symbol>>().source = this }
        attempt.input.removeSave()
        result
    }
}

/**
 * A symbol [imported][ParserDefinition.import] from another parser.
 */
public sealed class ForeignSymbol<UnnamedT : NameableSymbol<out UnnamedT>>(
    named: NamedSymbol<out UnnamedT>,
) : NamedSymbol<UnnamedT>(named.name, named.unnamed) {
    internal abstract val origin: Parser

    final override fun match(attempt: ParsingAttempt) = matching(attempt) {
        val previousSkip = attempt.skip
        attempt.skip = (origin as? LexerlessParser)?.resolveSkip()
        val result = super.match(attempt)
        attempt.skip = previousSkip
        attempt.input.removeSave()
        result
    }
}

/**
 * A foreign symbol originating from a [NullaryParser].
 */
public class NullaryForeignSymbol<UnnamedT: NameableSymbol<out UnnamedT>> internal constructor(
    named: NamedSymbol<out UnnamedT>,
    override val origin: NullaryParser
) : ForeignSymbol<UnnamedT>(named)

/**
 * A foreign symbol originating from a [UnaryParser].
 */
public class UnaryForeignSymbol<UnnamedT: NameableSymbol<out UnnamedT>, in ArgumentT> internal constructor(
    named: NamedSymbol<out UnnamedT>,
    override val origin: UnaryParser<ArgumentT>
) : ForeignSymbol<UnnamedT>(named)

/**
 * A symbol representing a symbol that is not a [TypeUnsafeSymbol].
 */
public sealed class SimpleSymbol<Self : SimpleSymbol<Self>> : NameableSymbol<Self>() {
    final override fun unwrap() = super.unwrap()
}

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
    abstract override fun resolveRawName(): String
}

/**
 * A symbol used to produce tokens during lexical analysis.
 *
 * Ensures separation between parser symbols literals.
 */
public class LexerSymbol internal constructor(
    private val start: Fragment,
    private val behavior: Behavior = Behavior.NOTHING
) : NameableSymbol<LexerSymbol>(), LexerComponent {
    /**
     * Can be combined with other fragments to create a named [LexerSymbol].
     */
    public class Fragment internal constructor(
        internal val root: Symbol
    ) : ParserComponent(), LexerComponent {
        override val rawName get() = root.rawName

        internal fun lex(data: ParsingAttempt) = root.match(data)?.substring

        override fun unwrap() = root.unwrap()
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
            val NOTHING = Behavior {}
        }
    }

    override fun unwrap() = start.unwrap()

    override fun match(attempt: ParsingAttempt) = matching(attempt) {
        val result = start.lex(attempt)?.let {
            attempt.modeStack.apply(behavior.action)
            SyntaxTreeNode(this, it)
        }
        attempt.input.removeSave()
        result
    }

    override fun resolveRawName() = start.rawName
}

// ------------------------------ specialized symbols ------------------------------

/**
 * A symbol matching a string of characters.
 */
public class Text internal constructor(private val literal: String) : SimpleSymbol<Text>() {
    internal constructor(query: Char) : this(query.toString())

    override fun match(attempt: ParsingAttempt) = matching(attempt) {
        val input = attempt.input
        val matchExists = if (input is CharRevertibleIterator) {
            literal.all { input.hasNext() && it == input.next() }
        } else if (input.isExhausted()) {
            false
        } else {
            rawName == (input.next() as Token).name
        }
        input.revert()
        takeIf { matchExists }?.let {
            input.advance(if (input is CharRevertibleIterator) literal.length else 1)
            SyntaxTreeNode(this, literal)
        }
    }

    override fun resolveRawName() = "\"${literal.withEscapes()}\""
}

/**
 * A symbol matching a single character agreeing with a set of ranges and exact characters.
 */
public class Switch internal constructor(
    private val literal: String,    // Before optimization
    private val ranges: List<CharRange>
) : SimpleSymbol<Switch>() {
    override fun match(attempt: ParsingAttempt) = matching(attempt) {
        val input = attempt.input
        var substring = ""
        val matchExists = if (input.isExhausted()) {
            false
        } else if (input is CharRevertibleIterator) {
            val next = input.peek()
            substring = next.toString()
            next satisfies ranges
        } else {
            val next = input.peek() as Token
            substring = next.substring
            rawName != next.name
        }
        input.revert()
        takeIf { matchExists }?.let {
            input.advance(1)
            SyntaxTreeNode(this, substring)
        }
    }

    override fun resolveRawName() = "[${literal.withEscapes()}]"

    public companion object {
        /**
         * Matches any single character.
         */
        public val ANY_CHAR: Switch = Switch("-", listOf(Char.MIN_VALUE..Char.MAX_VALUE))
    }
}

/**
 * A symbol matching another symbol one or more times in a row.
 *
 * If any one match is of an empty substring, no more matches are attempted.
 */
public class Repetition<SubMatchT : Symbol>(private val query: SubMatchT) : SimpleSymbol<Repetition<SubMatchT>>() {
    override fun match(attempt: ParsingAttempt) = matching(attempt) {
        val input = attempt.input
        var subMatch: SyntaxTreeNode<SubMatchT>? = query.match(attempt).unsafeCast()
        val subMatches = mutableListOf<SyntaxTreeNode<SubMatchT>>()
        debug { "Attempting matches to query: $query" }
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
        takeIf { subMatches.isNotEmpty() }?.let {
            val result = RepetitionNode(this, subMatches.concatenate(), subMatches)
            input.advance(if (input is CharRevertibleIterator) result.substring.length else result.branches.size)
            result
        }
    }

    override fun resolveRawName() = query.parenthesizeIf<TypeSafeSymbol<*, *>>() + "+"
}

/**
 * A symbol matching another symbol, or a zero-length token if that symbol is not found.
 */
public class Option<SubMatchT : Symbol>(private val query: SubMatchT) : SimpleSymbol<Option<SubMatchT>>() {
    private val emptyMatch = OptionNode(this, "", null)

    override fun match(attempt: ParsingAttempt): SyntaxTreeNode<*> { // Fail check & pivoting unnecessary
        debug { "Matching to query ${query.rawName} or empty match" }
        attempt.here().symbols += this
        val result = query.match(attempt)?.let { OptionNode(this, it.substring, it.unsafeCast()) } ?: emptyMatch
        debugMatchSuccess(result)
        return result
    }

    override fun resolveRawName() = query.parenthesizeIf<TypeSafeSymbol<*, *>>() + "?"
}

/**
 * A symbol matching the first of any but one named symbol in the parser this symbol originates from.
 *
 * If the affiliated parser is a [NameableLexerParser], match attempts are only made to lexer symbols.
 */
public class Inversion(
    private val exclusion: NamedSymbol<*>
) : SimpleSymbol<Inversion>() {
    internal var origin: Parser by OnceAssignable(raise = ::IllegalStateException)

    override fun match(attempt: ParsingAttempt) = matching(attempt) {
        origin.resolveSymbols().values.asSequence()
            .mapNotNull {
                debug { "Attempting match to query: ${it.rawName}" }
                it.match(attempt)
            }
            .firstOrNull()
    }

    override fun resolveRawName() = "!$exclusion"
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

    override fun match(attempt: ParsingAttempt) = matching(attempt) {
        val result = components.asSequence()
            .filter { it !in attempt.here().symbols /* prevent infinite recursion */ }
            .map {
                debug { "Attempting match to query: ${it.rawName}" }
                it.match(attempt)
            }
            .withIndex()
            .find { it.value != null }
            ?.unsafeCast<IndexedValue<SyntaxTreeNode<*>>>()
            ?.let { JunctionNode(typeSafe, it.value.substring, it.value, it.index) }
        attempt.input.removeSave()
        result
    }

    override fun resolveRawName() = components.joinToString(" | ")
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

    override fun match(attempt: ParsingAttempt) = matching(attempt) {
        val input = attempt.input
        val firstQuery = components.first()
        if (firstQuery !is TypeUnsafeJunction<*> && firstQuery in attempt.here().symbols) {
            // Prevent infinite recursion. Does not apply to junctions since they already handle infinite recursion
            debug { "Left-recursion found for non-junction query ${firstQuery.rawName}" }
            input.removeSave()
            return@matching null
        }
        var subMatch: SyntaxTreeNode<*>?
        val subMatches = mutableListOf<SyntaxTreeNode<*>>()
        for (query in components) {
            subMatch = query.match(attempt)
            if (subMatch == null) {
                input.revert()
                return@matching null
            }
            subMatches.add(subMatch)
            if (subMatch.substring.isNotEmpty()) {
                align(attempt)
            }
        }
        input.removeSave()
        SequenceNode(typeSafe, subMatches.concatenate(), subMatches)
    }

    override fun resolveRawName() = components.joinToString(" ") { it.parenthesizeIf<TypeSafeJunction<*>>() }

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
public class End : NameableSymbol<End>() {
    override fun match(attempt: ParsingAttempt): SyntaxTreeNode<*>? {
        debug { "Matching to end of input" }
        return if (attempt.input.isExhausted()) {
            SyntaxTreeNode(this, "").apply(::debugMatchSuccess)
        } else {
            debugMatchFail()
            null
        }
    }

    override fun resolveRawName() = "<end of input>"
}