package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.TypeSafeJunction
import io.github.aeckar.parsing.typesafe.TypeSafeSequence
import io.github.aeckar.parsing.typesafe.TypeSafeSymbol
import io.github.aeckar.parsing.utils.Named

// Symbols must be reused for lexical analysis to preserve type-safety

private inline fun <reified T> Symbol.parenthesizeIf() = if (this is T) "($this)" else toString()
// ------------------------------ generic symbols ------------------------------

/**
 * A symbol used to parse a specific kind of token in a given input.
 */
public abstract class Symbol internal constructor() : ParserComponent {
    /**
     * The name assigned to this symbol if it exists, else its EBNF representation.
     */
    internal abstract val rawName: String

    internal abstract fun match(data: ParserMetadata): Node<*>?
}

/**
 * A symbol that can be given a name by delegating it to a property.
 *
 * Delegating an instance of this class to a property produces a [NamedSymbol].
 *
 * Doing so enables:
 * - [Importing][DefinitionDsl.import] of symbols from other parsers
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
@Suppress("unused") // Type parameter used in extensions
public class NamedSymbol<UnnamedT : NameableSymbol<UnnamedT>>(
    override val name: String,
    internal var unnamed: NameableSymbol<UnnamedT>
) : Symbol(), Named {
    override val rawName: String get() = name

    override fun match(data: ParserMetadata): Node<*>? = unnamed.match(data)
    override fun toString(): String = name
}

/**
 * A symbol representing a symbol that is not a [TypeUnsafeSymbol].
 */
public sealed class BasicSymbol<InheritorT : BasicSymbol<InheritorT>> : NameableSymbol<InheritorT>()

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

    // Will not be called before all components are assembled
    abstract override fun resolveRawName(): String
}

/**
 * A symbol used to produce tokens during lexical analysis.
 *
 * Ensures separation between parser symbols literals.
 */
public class LexerSymbol(private val start: Fragment) : NameableSymbol<LexerSymbol>() {
    override fun match(data: ParserMetadata) = start.match(data)?.let { Node(this, it) }
    override fun resolveRawName() = start.rawName
}

// ------------------------------ specialized symbols ------------------------------

/**
 * A symbol matching a string of characters.
 */
public class Text internal constructor(private val query: String) : BasicSymbol<Text>() {
    internal constructor(query: Char) : this(query.toString())

    override fun match(data: ParserMetadata): Node<*>? {
        TODO("Not yet implemented")
    }

    override fun resolveRawName() = "\"$query\""
}

/**
 * A symbol matching a single character agreeing with a set of ranges and exact characters.
 */
public class Switch internal constructor(
    private val switch: String,
    internal val ranges: List<CharRange>
) : BasicSymbol<Switch>() {
    override fun match(data: ParserMetadata): Node<*>? {
        TODO("Not yet implemented")
    }

    override fun resolveRawName() = "[$switch]"

    internal companion object {
        val ANY_CHAR = Switch("-", listOf(Char.MIN_VALUE..Char.MAX_VALUE))
    }
}

/**
 * A symbol matching another symbol one or more times in a row.
 */
public class Repetition<SubMatchT : Symbol>(private val query: SubMatchT) : BasicSymbol<Repetition<SubMatchT>>() {
    override fun match(data: ParserMetadata): Node<*>? {
        TODO("Not yet implemented")
    }

    override fun resolveRawName() = query.parenthesizeIf<TypeSafeSymbol<*, *>>() + "+"
}

/**
 * A symbol matching another symbol, or a zero-length token if that symbol is not found.
 */
public class Option<SubMatchT : Symbol>(private val query: SubMatchT) : BasicSymbol<Option<SubMatchT>>() {
    override fun match(data: ParserMetadata): Node<*>? {
        TODO("Not yet implemented")
    }

    override fun resolveRawName() = query.parenthesizeIf<TypeSafeSymbol<*, *>>() + "?"
}

/**
 * A symbol matching one of several possible other symbols.
 */
public class Junction<TypeSafeT : TypeSafeJunction<TypeSafeT>>
internal constructor() : TypeUnsafeSymbol<TypeSafeT, Junction<TypeSafeT>>() {
    override fun match(data: ParserMetadata): Node<*>? {
        TODO("Not yet implemented")
    }

    override fun resolveRawName() = components.joinToString(" | ")
}

/**
 * A symbol matching multiple symbols in a certain order.
 */
public class Sequence<TypeSafeT : TypeSafeSequence<TypeSafeT>>
internal constructor() : TypeUnsafeSymbol<TypeSafeT, Sequence<TypeSafeT>>() {
    override fun match(data: ParserMetadata): Node<*>? {
        TODO("Not yet implemented")
    }

    override fun resolveRawName() = components.joinToString(" ") { parenthesizeIf<TypeSafeJunction<*>>() }
}