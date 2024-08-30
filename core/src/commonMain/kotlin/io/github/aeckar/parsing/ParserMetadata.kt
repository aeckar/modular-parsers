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
     * A stack of symbols that have been called, including the current one
     */
    val callStack: MutableList<Symbol> = mutableListOf()

    /**
     * Symbols that have been failed to match at each subsequent position during parsing.
     */
    val failStack: MutableList<MutableList<Symbol>> = mutableListOf(mutableListOf())

    override fun toString(): String {
        return "{ input = $input, skip = $skip, callStack = $callStack, failStack = $failStack }"
    }
}