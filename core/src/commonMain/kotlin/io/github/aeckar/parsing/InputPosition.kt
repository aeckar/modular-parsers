package io.github.aeckar.parsing

/**
 * Represents a unique position in some input.
 *
 * Whenever the input is advanced, a new position object is created.
 */
internal class InputPosition {
    /**
     * Symbols that have been previously called at the current position in the input.
     */
    val symbols = mutableSetOf<Symbol>()

    /**
     * Symbols that have previously failed to match at the current position in the input.
     */
    val fails = mutableSetOf<Symbol>()

    /**
     * Symbols that have previously been matched at the current position in the input.
     */
    val successes = mutableMapOf<Symbol, SyntaxTreeNode<*>>()
}