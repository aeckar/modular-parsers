package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.TypeSafeJunction
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
 * An unnamed symbol.
 */
public abstract class UnnamedSymbol internal constructor() : Symbol() {
    final override val rawName: String by lazy { resolveRawName() }

    /**
     * The returned name is not enclosed in parentheses.
     */
    internal abstract fun resolveRawName(): String
}

/**
 * A symbol that can be given a name by delegating it to a property.
 *
 * Delegating an instance of this class to a property produces a [named symbol][NamedSymbol].
 *
 * Doing so enables:
 * - [Importing][ParserDsl.import] of symbols from other parsers
 * - Definition of [recursive][TypeSafeSymbol] symbols
 */
public abstract class NameableSymbol<S : Symbol> internal constructor() : UnnamedSymbol(), Nameable {
    override fun getValue(thisRef: Any?, property: KProperty<*>): NamedSymbol<S> {
        return NamedSymbol(property.name, this.unsafeCast())
    }
}

/**
 * A symbol given a name by being delegated to a property.
 */
public class NamedSymbol<S : Symbol>(
    override val name: String,
    internal var named: S
) : Symbol(), Named {
    override val rawName: String get() = name

    override fun match(lexer: Lexer) = named.match(lexer)
}

/**
 * A symbol comprised of more than one other symbol.
 *
 * Can only be named by wrapping this instance in a typed subclass (`<subclass>2`, `<subclass>3`, ...).
 */
public sealed class ComplexSymbol<S : ComplexSymbol<S>> : NameableSymbol<S>() {
    internal val components = mutableListOf<Symbol>()

    // Will not be called before all components are assembled
    abstract override fun resolveRawName(): String
}

// ------------------------------ specialized symbols ------------------------------

/**
 * A symbol matching a string of characters.
 */
public class Text internal constructor(internal val query: String) : NameableSymbol<Text>(), Nameable {
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
    internal val switch: String,
    internal val ranges: List<CharRange>
) : NameableSymbol<Switch>(), Nameable {
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
public class Repetition<S : Symbol>(internal val query: S) : NameableSymbol<Repetition<S>>(), Nameable {
//  todo  fun ParserDsl. eweflmsefms;emf;slemflsefsme

    override fun match(lexer: Lexer): Symbol? {
        TODO("Not yet implemented")
    }

    override fun resolveRawName() = query.parenthesizeIf<TypeSafeSymbol<*>>() + "+"
}

/**
 * A symbol matching another symbol, or a zero-length token if that symbol is not found.
 */
public class Option<S : Symbol>(internal val query: S) : NameableSymbol<Option<S>>() {
    override fun match(lexer: Lexer): Symbol? {
        TODO("Not yet implemented")
    }

    override fun resolveRawName() = query.parenthesizeIf<TypeSafeSymbol<*>>() + "?"
}

/**
 * A symbol matching one of several possible other symbols.
 */
public class Junction internal constructor() : ComplexSymbol<Junction>() {
    override fun match(lexer: Lexer): Symbol? {
        TODO("Not yet implemented")
    }

    override fun resolveRawName() = components.joinToString(" | ")
}

/**
 * A symbol matching multiple symbols in a certain order.
 */
public class Sequence internal constructor() : ComplexSymbol<Sequence>() {
    override fun match(lexer: Lexer): Symbol? {
        TODO("Not yet implemented")
    }

    override fun resolveRawName() = components.joinToString(" ") { parenthesizeIf<TypeSafeJunction<*>>() }
}