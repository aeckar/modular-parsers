package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.TypeSafeJunction
import io.github.aeckar.parsing.typesafe.TypeSafeSequence
import io.github.aeckar.parsing.typesafe.TypeSafeSymbol
import io.github.aeckar.parsing.utils.unsafeCast
import kotlin.reflect.KProperty

// Symbols must be reused for lexical analysis to preserve type-safety

// ------------------------------ generic symbols ------------------------------

/**
 * A symbol used to parse a specific kind of token in a given input.
 */
public abstract class Symbol internal constructor() {
    /**
     * The name assigned to this symbol if it exists, else its EBNF representation.
     *
     * Access by user restricted to listener DSL.
     * Should be preferred over [toString].
     */
    internal abstract val rawName: String

    internal abstract fun match(lexer: Lexer): Symbol?

    internal inline fun <reified T> parenthesizeIf() = if (this is T) "($this)" else toString()
}

/**
 * A symbol that can be given a name by delegating it to a property.
 *
 * Delegating an instance of this class to a property produces a [named symbol][NamedSymbol].
 *
 * Doing so enables:
 * - [Importing][ParserDefinitionDsl.import] of symbols from other parsers
 * - Definition of [recursive][TypeSafeSymbol] symbols
 *
 * @param S the inheritor of this class
 */
public abstract class NameableSymbol<S : Symbol> internal constructor() : Symbol() {
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
public class NamedSymbol<S : Symbol>(
    override val name: String,
    internal var unnamed: Symbol
) : Symbol(), Named {
    override val rawName: String get() = name

    override fun match(lexer: Lexer) = unnamed.match(lexer)
    override fun toString(): String = name
}

/**
 * A symbol comprised of more than one other symbol.
 *
 * Can only be named by wrapping this instance in a typed subclass (`<subclass>2`, `<subclass>3`, ...).
 *
 * @param U the type-safe variant of this class
 * @param S the inheritor of this class
 */
public sealed class ComplexSymbol<U : TypeSafeSymbol<*, *>, S : ComplexSymbol<U, S>> : NameableSymbol<S>() {
    internal val components = mutableListOf<Symbol>()

    // Will not be called before all components are assembled
    abstract override fun resolveRawName(): String
}

// ------------------------------ specialized symbols ------------------------------

/**
 * A symbol matching a string of characters.
 */
public class Text internal constructor(private val query: String) : NameableSymbol<Text>() {
    internal constructor(query: Char) : this(query.toString())

    override fun match(lexer: Lexer): Symbol? {
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
) : NameableSymbol<Switch>() {
    override fun match(lexer: Lexer): Symbol? {
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
public class Repetition<S : Symbol>(private val query: S) : NameableSymbol<Repetition<S>>() {
//  todo  fun ParserDsl. eweflmsefms;emf;slemflsefsme

    override fun match(lexer: Lexer): Symbol? {
        TODO("Not yet implemented")
    }

    override fun resolveRawName() = query.parenthesizeIf<TypeSafeSymbol<*, *>>() + "+"
}

/**
 * A symbol matching another symbol, or a zero-length token if that symbol is not found.
 */
public class Option<S : Symbol>(private val query: S) : NameableSymbol<Option<S>>() {
    override fun match(lexer: Lexer): Symbol? {
        TODO("Not yet implemented")
    }

    override fun resolveRawName() = query.parenthesizeIf<TypeSafeSymbol<*, *>>() + "?"
}

/**
 * A symbol matching one of several possible other symbols.
 */
public class Junction<S : TypeSafeJunction<S>> internal constructor() : ComplexSymbol<S, Junction<S>>() {
    override fun match(lexer: Lexer): Symbol? {
        TODO("Not yet implemented")
    }

    override fun resolveRawName() = components.joinToString(" | ")
}

/**
 * A symbol matching multiple symbols in a certain order.
 */
public class Sequence<S : TypeSafeSequence<S>> internal constructor() : ComplexSymbol<S, Sequence<S>>() {
    override fun match(lexer: Lexer): Symbol? {
        TODO("Not yet implemented")
    }

    override fun resolveRawName() = components.joinToString(" ") { parenthesizeIf<TypeSafeJunction<*>>() }
}