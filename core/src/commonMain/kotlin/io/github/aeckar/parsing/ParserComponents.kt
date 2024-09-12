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

public sealed class ParserComponent {
    /**
     * The name assigned to this symbol if it exists, else its EBNF representation.
     */
    internal abstract val rawName: String

    /**
     * If this symbol is a wrapper of another symbol, returned the wrapped instance.
     *
     * Symbol wrappers include [TypeSafeSymbol] and [NamedSymbol].
     */
    internal open fun unwrap(): Symbol = this as Symbol

    /**
     * Returns the name assigned to this symbol if it exists, else its EBNF representation.
     */
    final override fun toString(): String = rawName

    final override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return rawName == (other as? ParserComponent)?.rawName
    }

    final override fun hashCode(): Int = rawName.hashCode()
}