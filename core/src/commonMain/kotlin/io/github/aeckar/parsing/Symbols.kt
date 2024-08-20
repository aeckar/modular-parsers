package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.*
import io.github.aeckar.parsing.typesafe.JunctionNode
import io.github.aeckar.parsing.typesafe.SequenceNode
import io.github.aeckar.parsing.utils.*

private inline fun <reified T> Symbol.parenthesizeIf() = if (this is T) "($this)" else toString()

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
     * If this symbol wraps another symbol, returned the wrapped instance.
     *
     * Applicable to [TypeSafeSymbol] and [NamedSymbol].
     */
    internal open fun resolve() = this

    internal abstract fun match(stream: SymbolStream): Node<*>?

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

    final override fun resolve() = unnamed

    final override fun match(stream: SymbolStream) : Node<*>? {
        return unnamed.match(stream)?.also { it.unsafeCast<Node<Symbol>>().source = this }
    }

    final override fun toString(): String = name
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
public sealed class BasicSymbol<InheritorT : BasicSymbol<InheritorT>> : NameableSymbol<InheritorT>() {
    final override fun resolve() = super.resolve()
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
    internal val components = mutableListOf<Symbol>()

    final override fun resolve() = super.resolve()

    // Will not be called before all components are assembled
    abstract override fun resolveRawName(): String
}

/**
 * A symbol used to produce tokens during lexical analysis.
 *
 * Ensures separation between parser symbols literals.
 */
public class LexerSymbol(private val start: SymbolFragment) : NameableSymbol<LexerSymbol>() {
    override fun resolve() = start.root.resolve()
    override fun match(stream: SymbolStream) = start.lex(stream)?.let { Node(this, it) }
    override fun resolveRawName() = start.rawName
}

// ------------------------------ specialized symbols ------------------------------

/**
 * A symbol matching a string of characters.
 */
public class Text internal constructor(private val query: String) : BasicSymbol<Text>() {
    internal constructor(query: Char) : this(query.toString())

    override fun match(stream: SymbolStream) = pivot(stream) {
        val matchExists = if (input is CharStream) {
            query.all { it == input.next() }
        } else {
            this@Text.rawName == (input as TokenStream).next().name
        }
        revertPosition()
        takeIf { matchExists }?.let {
            advancePosition(query.length, 1)
            Node(this@Text, query)
        }
    }

    override fun resolveRawName() = "\"${query.withEscapes()}\""
}

/**
 * A symbol matching a single character agreeing with a set of ranges and exact characters.
 */
public class Switch internal constructor(
    private val switch: String,
    private val ranges: List<CharRange>
) : BasicSymbol<Switch>() {
    override fun match(stream: SymbolStream) = pivot(stream) {
        val substring: String
        val matchExists = if (input is CharStream) {
            val next = input.peek()
            substring = next.toString()
            ranges.none { next in it }
        } else {
            val next = (input as TokenStream).peek()
            substring = next.substring
            this@Switch.rawName != next.name
        }
        revertPosition()
        takeIf { matchExists }?.let {
            advancePosition(1)
            Node(this@Switch, substring)
        }
    }

    override fun resolveRawName() = "[${switch.withEscapes()}]"

    public companion object {
        /**
         * Matches any single character.
         */
        public val ANY_CHAR: Switch = Switch("-", listOf(Char.MIN_VALUE..Char.MAX_VALUE))
    }
}

/**
 * A symbol matching another symbol one or more times in a row.
 */
public class Repetition<SubMatchT : Symbol>(private val query: SubMatchT) : BasicSymbol<Repetition<SubMatchT>>() {
    override fun match(stream: SymbolStream) = pivot(stream) {
        val children = mutableListOf<Node<SubMatchT>>()
        var subMatch: Node<SubMatchT>? = query.match().unsafeCast()
        while (subMatch != null) {
            children += subMatch
            if (subMatch.substring.isEmpty()) {
                break
            }
            subMatch = query.match().unsafeCast()
        }
        revertPosition()
        takeIf { children.isNotEmpty() }?.let {
            val match = RepetitionNode(this@Repetition, children.concatenate(), children)
            advancePosition(match.substring.length, match.branches.size)
            match
        }
    }

    override fun resolveRawName() = query.parenthesizeIf<TypeSafeSymbol<*, *>>() + "+"
}

/**
 * A symbol matching another symbol, or a zero-length token if that symbol is not found.
 */
public class Option<SubMatchT : Symbol>(private val query: SubMatchT) : BasicSymbol<Option<SubMatchT>>() {
    private val emptyMatch = OptionNode(this, "", null)

    override fun match(stream: SymbolStream): Node<*> {
        return query.match(stream)?.let { OptionNode(this@Option, it.substring, it.unsafeCast()) } ?: emptyMatch
    }

    override fun resolveRawName() = query.parenthesizeIf<TypeSafeSymbol<*, *>>() + "?"
}

/**
 * A symbol matching one of several possible other symbols.
 */
public class ImplicitJunction<TypeSafeT : TypeSafeJunction<TypeSafeT>> internal constructor(
) : TypeUnsafeSymbol<TypeSafeT, ImplicitJunction<TypeSafeT>>() {
    internal lateinit var typed: TypeSafeJunction<*>

    internal constructor(option1: Symbol, option2: Symbol) : this() {
        components += option1
        components += option2
    }

    override fun match(stream: SymbolStream): Node<*>? {
        if (this in stream.failCache) {
            return null
        }
        return components.asSequence()
            .filter { it !in stream.recursions && it !in stream.failCache }
            .map { it.match(stream) }
            .withIndex()
            .find { it.value != null }
            ?.unsafeCast<IndexedValue<Node<*>>>()
            ?.let { JunctionNode(typed, it.value.substring, it.value, it.index) }
    }

    override fun resolveRawName() = components.joinToString(" | ")
}

/**
 * A symbol matching multiple symbols in a certain order.
 */
public class ImplicitSequence<TypeSafeT : TypeSafeSequence<TypeSafeT>> internal constructor(
) : TypeUnsafeSymbol<TypeSafeT, ImplicitSequence<TypeSafeT>>() {
    internal lateinit var typed: TypeSafeSequence<*>

    internal constructor(query1: Symbol, query2: Symbol) : this() {
        components += query1
        components += query2
    }

    override fun match(stream: SymbolStream) = pivot(stream) {
        val components = components.iterator()
        val first = components.next().resolve()
        if (first !is ImplicitJunction<*> && first in recursions) { // Infinite recursion check
            removeSavedPosition()
            return@pivot null
        }

        // First iteration
        savePosition()
        var subMatch = first.match(stream)
        if (subMatch == null) {
            revertPosition()
            return@pivot null
        }
        val branches = mutableListOf<Node<*>>()
        branches += subMatch
        if (input is CharStream && subMatch.substring.isNotEmpty()) {
            skip!!.match(stream)
        }

        failCache.clear()
        while (components.hasNext()) {
            val component = components.next()
            subMatch = component.match(stream)
            if (subMatch == null) {
                revertPosition()
                return@pivot null
            }
            branches += subMatch
            if (input is CharStream && subMatch.substring.isNotEmpty()) {
                skip!!.match(stream)
            }
        }
        removeSavedPosition()
        SequenceNode(typed, branches.concatenate(), branches)
    }

    override fun resolveRawName() = components.joinToString(" ") { parenthesizeIf<TypeSafeJunction<*>>() }

    public companion object {
        /**
         * Matches all characters up to, and including, a newline (`'\n'`) character.
         */
        public val LINE: ImplicitSequence<*> = ImplicitSequence().apply {
            val ranges = listOf(Char.MIN_VALUE..'\u0009', '\u000b'..Char.MAX_VALUE)
            components += Repetition(Switch("-\u0009\u000b-", ranges))
            components += Text("\n")
        }
    }
}