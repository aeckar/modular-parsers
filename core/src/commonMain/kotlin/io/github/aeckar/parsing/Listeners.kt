package io.github.aeckar.parsing

/**
 * An action performed whenever a successful match is made using a symbol that emits the given token.
 */
public fun interface Listener<T : Symbol> {
    /**
     * Invokes the lambda that defines the listener of a specific named symbol.
     */
    public operator fun invoke(token: Token<T>)
}

/**
 * An action performed from a [unary][unaryParser] or [dynamic][dynamicParser] parser whenever a
 * successful match is made using a symbol that emits the given token.
 */
public fun interface ListenerWithArgument<T : Symbol, A> {
    /**
     * Invokes the lambda that defines the listener of a specific named symbol.
     *
     * Includes the argument passed to the parser.
     */
    public operator fun invoke(token: Token<T>, argument: A)
}