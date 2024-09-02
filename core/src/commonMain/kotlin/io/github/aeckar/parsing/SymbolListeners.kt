package io.github.aeckar.parsing

/**
 * TODO
 */
@DslMarker
public annotation class ListenerDsl

/**
 * An action performed whenever a successful match is made to a symbol.
 */
public sealed interface SymbolListener

/**
 * A listener assigned to a symbol that emits the given node.
 */
public fun interface NullarySymbolListener<MatchT : Symbol> : SymbolListener {
    /**
     * Invokes the lambda that defines the listener of a specific named symbol.
     */
    public operator fun SyntaxTreeNode<MatchT>.invoke()
}

/**
 * A listener that takes an argument and includes the argument passed to the parser.
 */
public fun interface UnarySymbolListener<MatchT : Symbol, in ArgumentT> : SymbolListener {
    /**
     * Invokes the lambda that defines the listener of a specific named symbol.
     *
     * Includes the argument passed to the parser.
     */
    public operator fun SyntaxTreeNode<MatchT>.invoke(argument: ArgumentT)
}