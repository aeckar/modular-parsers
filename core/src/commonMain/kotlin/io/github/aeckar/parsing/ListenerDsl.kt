package io.github.aeckar.parsing

/**
 * Denotes a nested scope within a symbol listener.
 *
 * Restricts the creation of component scopes within other components of the same symbol.
 */
@DslMarker
public annotation class ListenerDsl