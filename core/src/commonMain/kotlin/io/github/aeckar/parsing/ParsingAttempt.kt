package io.github.aeckar.parsing

/**
 * Contains important variables related to parsing.
 *
 * Passed to [Symbol.match].
 * @param input a character or token stream that instances of this class delegate to
 * @param skip the skip symbol of the parser
 * For each match to a non-empty substring, the stack grows. During backtracking, it shrinks
 */
internal class ParsingAttempt(
    val input: InputIterator<*, *>,
    var skip: Symbol?
) {
    /**
     * The stack of lexer modes that are currently active.
     */
    var modeStack = mutableListOf("")
}

