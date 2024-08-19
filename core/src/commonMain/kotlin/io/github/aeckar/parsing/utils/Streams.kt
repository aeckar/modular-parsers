package io.github.aeckar.parsing.utils

import io.github.aeckar.parsing.Node
import io.github.aeckar.parsing.Symbol
import io.github.aeckar.parsing.Token
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readString
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Returns null if any stream used within [block] is exhausted, and saves the position of the stream beforehand.
 *
 * If null is returned by [block] or the stream is exhausted,
 * the match to the receiver has failed and the failure is cached.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <ReturnT> Symbol.pivot(
    stream: SymbolStream,
    crossinline block: SymbolStream.() -> ReturnT
): ReturnT? {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    stream.savePosition()
    return try {
        block(stream)
    } catch (_: StreamExhaustedSignal) {
        null
    } ?: null.also {
        stream.failCache += this
    }
}

/**
 * Returns the value returned by the supplied lambda, or throws [StreamExhaustedSignal] if the stream is exhausted.
 */
private inline fun <ReturnT> signalExhausted(block: () -> ReturnT): ReturnT {
    return try {
        block()
    } catch (_: IndexOutOfBoundsException) {
        throw StreamExhaustedSignal
    }
}

// ------------------------------ basic streams ------------------------------

internal object StreamExhaustedSignal : Throwable()

/**
 * If this stream is a [CharStream], the position is advanced by [charCount].
 * Else, this stream is treated as a [TokenStream] and the position is advanced by [tokenCount]
 */
internal fun Stream.advancePosition(charCount: Int, tokenCount: Int) {
    advancePosition(if (this is CharStream) charCount else tokenCount)
}

/**
 * A sequence of values that can be iterated through incrementally.
 */
internal interface Stream : AutoCloseable {
    fun advancePosition(places: Int)
    fun savePosition()
    fun revertPosition()
    fun removeSavedPosition()
    fun hasNext(): Boolean
}

internal sealed interface CharStream : Stream {
    fun next(): Char
    fun peek(): Char
}

/**
 * A stream whose entire contents are loaded during initialization.
 *
 * Invoking [close] on instances of this class does nothing.
 */
internal abstract class UnbufferedStream : Stream {
    protected var position = 0

    private val savedPositions = IntStack()

    final override fun advancePosition(places: Int) {
        position += places
    }

    final override fun savePosition() {
        savedPositions += position
    }

    final override fun revertPosition() {
        position = savedPositions.removeLast()
    }

    final override fun removeSavedPosition() {
        savedPositions.removeLast()
    }

    final override fun close() { /* no-op */ }
}

// ------------------------------ specialized streams ------------------------------

/**
 * Represents a stream of symbols within another [Stream].
 * @param input a character or token stream that instances of this class delegate to
 * @param skip the skip symbol of the parser
 * @param recursions TODO
 * @param failCache TODO
 */
internal class SymbolStream private constructor(
    val input: Stream,
    val skip: Symbol?,
    val recursions: MutableList<Symbol> = mutableListOf(),
    val failCache: MutableSet<Symbol> = mutableSetOf()
) : Stream by input {
    constructor(input: String, skip: Symbol?) : this(StringCharStream(input), skip)
    constructor(input: RawSource, skip: Symbol?) : this(SourceCharStream(input), skip)
    constructor(input: Stream) : this(input, null)

    fun Symbol.match(): Node<*>? = match(this@SymbolStream)

    override fun toString() = "{ input = $input, skip = $skip, recursions = $recursions, failCache = $failCache }"
}

internal class TokenStream(private val tokens: List<Token>) : UnbufferedStream() {
    fun next() = peek().also { ++position }
    fun peek() = signalExhausted { tokens[position] }

    override fun hasNext() = position < tokens.size
}

/**
 * An input stream backed by a string.
 */
internal class StringCharStream(private val chars: String) : UnbufferedStream(), CharStream {
    override fun next() = peek().also { ++position }
    override fun peek() = signalExhausted { chars[position] }
    override fun hasNext() = position < chars.length
}

/**
 * A buffered input stream backed by a [RawSource].
 */
internal class SourceCharStream(private val source: RawSource) : CharStream, AutoCloseable by source {
    private val buffer = mutableListOf<String>()
    private val savedPositions = mutableListOf<Position>()
    private var bufferSection = ""
    private var sectionPosition = 0

    private data class Position(val bufferSection: String, val sectionPosition: Int)

    override fun advancePosition(places: Int) {
        sectionPosition += places
    }

    override fun savePosition() {
        verifyBufferAndPosition()
        savedPositions += Position(bufferSection, sectionPosition)
    }

    override fun revertPosition() {
        savedPositions.removeLast().let { (section, position) ->
            bufferSection = section
            sectionPosition = position
        }
    }

    override fun removeSavedPosition() {
        savedPositions.removeLast()
    }

    override fun next() = peek().also { ++sectionPosition }

    override fun peek(): Char {
        verifyBufferAndPosition()
        return bufferSection[sectionPosition]
    }

    override fun hasNext() = sectionPosition < bufferSection.length || loadNextSection()

    private fun verifyBufferAndPosition() {
        while (sectionPosition >= bufferSection.length) {
            if (!loadNextSection()) {   // Input stream is exhausted
                throw StreamExhaustedSignal
            }
            sectionPosition -= bufferSection.length
            bufferSection = buffer.last()
        }
    }

    /**
     * Returns true if the next section exists, or false if this input stream has been exhausted.
     */
    private fun loadNextSection(): Boolean {
        val nextSection = Buffer().apply { source.readAtMostTo(this, SECTION_BYTES) }.readString()
        if (nextSection.isEmpty()) {
            return false
        }
        buffer += nextSection
        return true
    }

    private companion object {
        const val SECTION_BYTES = 8192L
    }
}