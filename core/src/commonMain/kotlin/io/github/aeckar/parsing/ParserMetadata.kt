package io.github.aeckar.parsing

import io.github.aeckar.parsing.utils.*
import kotlinx.io.RawSource

/**
 * Returns null if any stream used within [block] is exhausted,
 * saves the position of the stream beforehand,
 * and logs the match attempt.
 *
 * If null is returned by [block] or the stream is exhausted,
 * the match to the receiver has failed and the failure is cached.
 */
internal inline fun <MatchT : Node<*>?> Symbol.pivot(
    stream: ParserMetadata,
    crossinline block: ParserMetadata.() -> MatchT
): MatchT? {
    if (this in stream.failCache) {
        debugFail { "Previous attempt failed" }
        return null
    }
    stream.input.save()
    val result = block(stream)
    if (result != null) {
        debugSuccess(result)
    } else {
        debugFail()
        stream.failCache += this
    }
    return result
}

/**
 * Contains the mutable state supplied to [Symbol.match].
 * @param input a character or token stream that instances of this class delegate to
 * @param skip the skip symbol of the parser
 * @param failCache symbols that have previously been failed to match (cleared by [ImplicitSequence])
 */
internal class ParserMetadata private constructor(
    val input: PivotIterator<*>,
    val skip: Symbol?,
    val symbolCallStack: MutableList<Symbol> = mutableListOf(),
    val failCache: MutableSet<Symbol> = mutableSetOf()
) {
    constructor(input: String, skip: Symbol?) : this(input.pivotIterator(), skip)
    constructor(input: RawSource, skip: Symbol?) : this(input.pivotIterator(), skip)
    constructor(input: PivotIterator<*>) : this(input, null)

    fun Symbol.matchOnce(): Node<*>? = match(this@ParserMetadata)

    override fun toString() = "{ input = $input, skip = $skip, recursions = $symbolCallStack, failCache = $failCache }"
}