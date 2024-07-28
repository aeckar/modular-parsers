package io.github.aeckar.parsing

import io.github.aeckar.parsing.Symbol
import io.github.aeckar.parsing.Token

/**
 * An action performed whenever a successful match is made using the symbol that emits the given token.
 */
public fun interface Listener<T : Symbol> {
    /**
     * Invokes the lambda that defines the listener of a specific named symbol.
     */
    public operator fun invoke(token: Token<T>)
}