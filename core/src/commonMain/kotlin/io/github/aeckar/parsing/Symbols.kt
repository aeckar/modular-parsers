package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.*
import io.github.aeckar.parsing.utils.*

private inline fun <reified T> Symbol.parenthesizeIf() = if (this is T) "($this)" else toString()

/**
 * Returns the concatenation of the substrings of all elements in this list.
 */
private fun List<Node<*>>.concatenate() = joinToString("") { it.substring }

/**
 * Returns a string equal to this one with invisible characters expressed as their Kotlin escape character.
 */
private fun String.withEscapes() = buildString {
    this@withEscapes.forEach {
        if (it.isWhitespace() || it.isISOControl()) {
            append("\\u${it.code.toString(16).padStart(4, '0')}")
        } else {
            append(it)
        }
    }
}

// ------------------------------ generic symbols ------------------------------

/**
 * Used to parse a specific kind of token in a given input.
 *
 * The building block of a parser rule.
 */
public abstract class Symbol internal constructor() : ParserComponent {
    /**
     * The name assigned to this symbol if it exists, else its EBNF representation.
     */
    internal abstract val rawName: String

    /**
     * Returns null if this symbol is in the top of the fail stack, and logs the failure.
     * Otherwise, performs the following:
     * 1. Saves the current input position
     * 2. Logs whether the symbol was matched or not
     * 3. Appends this symbol to the top of the fail stack if not matched
     * 4. Returns the result
     */
    internal inline fun <MatchT : Node<*>?> matching(
        data: ParserMetadata,
        crossinline block: () -> MatchT
    ) = with(data) {
        if (this@Symbol in failStack.last()) {
            debugMatchFail { "Previous attempt failed" }
            return@with null
        }
        input.save()
        val result = block()
        if (result != null) {
            debugMatchSuccess(result)
        } else {
            debugMatchFail()
            failStack.last() += this@Symbol
        }
        result
    }

    /**
     * Performs the following:
     * 1. Consumes the next substring matching the skip symbol, if not null
     * 2. Pushes an empty list to the [fail stack][ParserMetadata.failStack]
     */
    internal fun align(data: ParserMetadata) {
        data.skip?.let {
            debug { "Attempting match to skip ${data.skip}" }
            it.match(data)
        }
        data.failStack += mutableListOf<Symbol>()
    }

    /**
     * If this symbol is a wrapper of another symbol, returned the wrapped instance.
     *
     * Symbol wrappers include [TypeSafeSymbol] and [NamedSymbol].
     */
    internal open fun unwrap() = this

    internal abstract fun match(data: ParserMetadata): Node<*>?
    
    final override fun toString(): String = rawName
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
public abstract class NameableSymbol<InheritorT : NameableSymbol<InheritorT>> internal constructor() : Symbol() {
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

    final override fun match(data: ParserMetadata) : Node<*>? {
        return unnamed.match(data)?.also { it.unsafeCast<Node<Symbol>>().source = this }
    }
}

/**
 * A symbol [imported][ParserDefinition.import] from another parser.
 */
public sealed class ForeignSymbol<UnnamedT : NameableSymbol<out UnnamedT>>(
    base: NamedSymbol<out UnnamedT>,
) : NamedSymbol<UnnamedT>(base.name, base.unnamed) {
    internal abstract val origin: Parser
}

/**
 * A foreign symbol originating from a [NullaryParser].
 */
public class NullaryForeignSymbol<UnnamedT: NameableSymbol<out UnnamedT>> internal constructor(
    base: NamedSymbol<out UnnamedT>,
    override val origin: NullaryParser
) : ForeignSymbol<UnnamedT>(base)

/**
 * A foreign symbol originating from a [UnaryParser].
 */
public class UnaryForeignSymbol<UnnamedT: NameableSymbol<out UnnamedT>, ArgumentT> internal constructor(
    base: NamedSymbol<out UnnamedT>,
    override val origin: UnaryParser<ArgumentT>
) : ForeignSymbol<UnnamedT>(base)

/**
 * A symbol representing a symbol that is not a [TypeUnsafeSymbol].
 */
public sealed class SimpleSymbol<InheritorT : SimpleSymbol<InheritorT>> : NameableSymbol<InheritorT>() {
    final override fun unwrap() = super.unwrap()
}

/**
 * A symbol comprised of more than one other symbol.
 *
 * Can only be named by wrapping this instance in a typed subclass (`<subclass>2`, `<subclass>3`, ...).
 */
public sealed class TypeUnsafeSymbol<
    TypeSafeT : TypeSafeSymbol<*, *>,
    InheritorT : TypeUnsafeSymbol<TypeSafeT, InheritorT>
> : NameableSymbol<InheritorT>() {
    protected val components: MutableList<Symbol> = mutableListOf()

    final override fun unwrap() = super.unwrap()

    // Will not be called before all components are assembled
    abstract override fun resolveRawName(): String
}

/**
 * A symbol used to produce tokens during lexical analysis.
 *
 * Ensures separation between parser symbols literals.
 */
public class LexerSymbol(private val start: SymbolFragment) : NameableSymbol<LexerSymbol>() {
    override fun unwrap() = start.root.unwrap()
    override fun match(data: ParserMetadata) = start.lex(data)?.let { Node(this, it) }
    override fun resolveRawName() = start.rawName
}

// ------------------------------ specialized symbols ------------------------------

/**
 * A symbol matching a string of characters.
 */
public class Text internal constructor(private val literal: String) : SimpleSymbol<Text>() {
    internal constructor(query: Char) : this(query.toString())

    override fun match(data: ParserMetadata) = matching(data) {
        val input = data.input
        val matchExists = if (input is CharPivotIterator) {
            literal.all { input.hasNext() && it == input.next() }
        } else if (input.isExhausted()) {
            false
        } else {
            rawName == (input.next() as Token).name
        }
        input.revert()
        takeIf { matchExists }?.let {
            input.advance(if (input is CharPivotIterator) literal.length else 1)
            Node(this, literal)
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
    override fun match(data: ParserMetadata) = matching(data) {
        val input = data.input
        var substring = ""
        val matchExists = if (input.isExhausted()) {
            false
        } else if (input is CharPivotIterator) {
            val next = input.peek()
            substring = next.toString()
            ranges.any { next in it }
        } else {
            val next = input.peek() as Token
            substring = next.substring
            rawName != next.name
        }
        input.revert()
        takeIf { matchExists }?.let {
            input.advance(1)
            Node(this, substring)
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
    override fun match(data: ParserMetadata) = matching(data) {
        val input = data.input
        var subMatch: Node<SubMatchT>? = query.match(data).unsafeCast()
        val subMatches = mutableListOf<Node<SubMatchT>>()
        debug { "Attempting matches to query ${query.rawName}" }
        while (subMatch != null) {
            subMatches += subMatch
            if (subMatch.substring.isEmpty()) { // No need to push to fail stack
                debug { "End matches to query (matched substring is empty)" }
                break
            }
            debug { "Attempting match to skip ${data.skip}" }
            align(data)
            subMatch = query.match(data).unsafeCast()
        }
        debug { "Query matched ${subMatches.size} times" }
        input.revert()
        takeIf { subMatches.isNotEmpty() }?.let {
            val result = RepetitionNode(this, subMatches.concatenate(), subMatches)
            input.advance(if (input is CharPivotIterator) result.substring.length else result.branches.size)
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

    override fun match(data: ParserMetadata): Node<*> {
        debug { "Matching to query ${query.rawName} or empty match" }
        val result = query.match(data)?.let { OptionNode(this, it.substring, it.unsafeCast()) } ?: emptyMatch
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
    internal var origin: Parser by OnceAssignable(throws = ::IllegalStateException)

    override fun match(data: ParserMetadata) = matching(data) {
        origin.parserSymbols.values.asSequence()
            .mapNotNull {
                debug { "Attempting match to query ${it.rawName}" }
                it.match(data)
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
    internal lateinit var typed: TypeSafeJunction<*>
    internal val options inline get() = components

    internal constructor(option1: Symbol, option2: Symbol) : this() {
        options += option1
        options += option2
    }

    override fun match(data: ParserMetadata) = matching(data) {
        val result = options.asSequence()
            .filter { it !in data.callStack /* prevent infinite recursion */ && it !in data.failStack.last() }
            .map {
                debug { "Attempting match to query ${it.rawName}" }
                it.match(data)
            }
            .withIndex()
            .find { it.value != null }
            ?.unsafeCast<IndexedValue<Node<*>>>()
            ?.let { JunctionNode(typed, it.value.substring, it.value, it.index) }
        data.input.removeSave()
        result
    }

    override fun resolveRawName() = options.joinToString(" | ")
}

/**
 * A symbol matching multiple symbols in a certain order.
 */
public class TypeUnsafeSequence<TypeSafeT : TypeSafeSequence<TypeSafeT>> internal constructor(
) : TypeUnsafeSymbol<TypeSafeT, TypeUnsafeSequence<TypeSafeT>>() {
    internal lateinit var typed: TypeSafeSequence<*>
    internal val queries inline get() = components

    internal constructor(query1: Symbol, query2: Symbol) : this() {
        queries += query1
        queries += query2
    }

    override fun match(data: ParserMetadata) = matching(data) {
        val input = data.input
        val firstQuery = queries.first()
        if (firstQuery !is TypeUnsafeJunction<*> && firstQuery in data.callStack) {
            // Prevent infinite recursion. Does not apply to junctions since they already handle infinite recursion
            debug { "Recursion found for non-junction query ${firstQuery.rawName}" }
            input.removeSave()
            return@matching null
        }
        var subMatch: Node<*>?
        val subMatches = mutableListOf<Node<*>>()
        for (query in queries) {
            subMatch = query.match(data)
            if (subMatch == null) {
                input.revert()
                return@matching null
            }
            subMatches += subMatch
            if (subMatch.substring.isNotEmpty()) {
                align(data)
            }
        }
        input.removeSave()
        SequenceNode(typed, subMatches.concatenate(), subMatches)
    }

    override fun resolveRawName() = queries.joinToString(" ") { it.parenthesizeIf<TypeSafeJunction<*>>() }

    public companion object {
        /**
         * Matches all characters up to, and including, a newline (`'\n'`) character.
         */
        public val LINE: TypeUnsafeSequence<*> = TypeUnsafeSequence().apply {
            val ranges = listOf(Char.MIN_VALUE..'\u0009', '\u000b'..Char.MAX_VALUE)
            queries += Repetition(Switch("-\u0009\u000b-", ranges))
            queries += Text("\n")
        }
    }
}