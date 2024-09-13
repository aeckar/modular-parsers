package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.TypeSafeSymbol

/**
 * A [LexerSymbol] or a [fragment][LexerSymbol.Fragment] of one.
 */
public sealed interface LexerComponent

/**
 * A parser component, possibly with a name.
 *
 * Can be delegated to a property to create a [NamedSymbol].
 */

public sealed class ParserComponent {   // Abstract class allows internal members
    /**
     * If this symbol is a wrapper of another symbol, returned the wrapped instance.
     *
     * Symbol wrappers include [TypeSafeSymbol] and [NamedSymbol].
     */
    internal abstract fun unwrap(): Symbol

    /**
     * Returns the name assigned to this symbol if it exists, else its EBNF representation.
     */
    abstract override fun toString(): String

    final override fun equals(other: Any?): Boolean = super.equals(other)
    final override fun hashCode(): Int = super.hashCode()
}