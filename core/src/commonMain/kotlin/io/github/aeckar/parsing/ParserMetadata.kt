package io.github.aeckar.parsing

import io.github.aeckar.parsing.utils.*
import kotlinx.io.RawSource

/**
 * Contains important variables related to parsing.
 *
 * Passed to [Symbol.match].
 * @param input a character or token stream that instances of this class delegate to
 * @param skip the skip symbol of the parser
 * @param callStack a stack of symbols that have been called, including the current one
 * @param failStack symbols that have been failed to match at each subsequent position during parsing.
 * For each match to a non-empty substring, the stack grows. During backtracking, it shrinks
 */
internal class ParserMetadata private constructor(
    val input: PivotIterator<*>,
    var skip: Symbol?,
    val callStack: MutableList<Symbol> = mutableListOf(),
    val failStack: MutableList<MutableList<Symbol>> = mutableListOf(mutableListOf())
) {
    constructor(input: String, skip: Symbol?) : this(input.pivotIterator(), skip)
    constructor(input: RawSource, skip: Symbol?) : this(input.pivotIterator(), skip)
    constructor(input: PivotIterator<*>) : this(input, null)

    override fun toString(): String {
        return "{ input = $input, skip = $skip, callStack = $callStack, failStack = $failStack }"
    }
}