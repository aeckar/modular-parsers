package io.github.aeckar.parsing

import io.github.aeckar.parsing.utils.*

/**
 * Contains important variables related to parsing.
 *
 * Passed to [Symbol.match].
 * @param input a character or token stream that instances of this class delegate to
 * @param skip the skip symbol of the parser
 * For each match to a non-empty substring, the stack grows. During backtracking, it shrinks
 */
internal class ParserMetadata(
    val input: PivotIterator<*>,
    var skip: Symbol?,
) {
    /**
     * The stack of symbols that have been called, including the current one
     */
    val callStack: MutableList<Symbol> = mutableListOf()

    /**
     * Symbols that have been failed to match,
     * with each subsequent position during parsing represented by its own member list.
     */
    val failStack: MutableList<MutableList<Symbol>> = mutableListOf(mutableListOf())

    /**
     * The stack of lexer modes that are currently active.
     */
    var modeStack: MutableList<String> = mutableListOf()

    override fun toString(): String {
        return "{ input = $input, skip = $skip, callStack = $callStack, failStack = $failStack }"
    }
}