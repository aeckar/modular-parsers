package io.github.aeckar.parsing

import io.github.aeckar.parsing.primitives.PivotIterator

/**
 * Contains important variables related to parsing.
 *
 * Passed to [Symbol.match].
 * @param input a character or token stream that instances of this class delegate to
 * @param skip the skip symbol of the parser
 * For each match to a non-empty substring, the stack grows. During backtracking, it shrinks
 */
internal class ParsingAttempt(
    val input: MatchIterator<*, *>,
    var skip: Symbol?
) {
    /**
     * The stack of lexer modes that are currently active.
     */
    var modeStack = mutableListOf("")
}

/**
 * Represents some unique position in [input].
 *
 * Whenever the input is advanced, a new stack frame is created.
 */
internal class MatchAttempt {
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