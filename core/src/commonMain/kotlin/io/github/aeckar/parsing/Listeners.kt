package io.github.aeckar.parsing

/**
 * Denotes a nested scope within a symbol listener.
 *
 * Restricts the creation of component scopes within other components of the same symbol.
 */
@DslMarker
public annotation class ListenerDsl

/**
 * A listener assigned to a symbol that emits the given node.
 */
public fun interface Listener<MatchT : Symbol> {
    /**
     * Invokes the lambda that defines the listener of a specific named symbol.
     */
    public operator fun SyntaxTreeNode<MatchT>.invoke()
}
