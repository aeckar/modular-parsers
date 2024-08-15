package io.github.aeckar.parsing

/**
 * An action performed whenever a successful match is made using a symbol that emits the given token.
 */
public fun interface NullaryListener<MatchT : Symbol> {
    /**
     * Invokes the lambda that defines the listener of a specific named symbol.
     */
    public operator fun Node<MatchT>.invoke()
}

/**
 * An action performed using an argument whenever a successful match is made using a symbol that emits the given token.
 */
public fun interface UnaryListener<MatchT : Symbol, ArgumentT> {
    /**
     * Invokes the lambda that defines the listener of a specific named symbol.
     *
     * Includes the argument passed to the parser.
     */
    public operator fun Node<MatchT>.invoke(argument: ArgumentT)
}