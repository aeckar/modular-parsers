package io.github.aeckar.parsing.utils

import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readString

/**
 * Returns the value returned by the supplied lambda,
 * or throws [InputStream.OutOfBoundsSignal] if the cursor is out of bounds.
 */
private inline fun <ReturnT> checkBounds(block: () -> ReturnT): ReturnT {
    return try {
        block()
    } catch (_: IndexOutOfBoundsException) {
        throw InputStream.OutOfBoundsSignal
    }
}

/**
 * A sequence of characters that can be iterated through incrementally.
 *
 * If an attempt to read a character is made and this stream is exhausted, [OutOfBoundsSignal] is thrown.
 */
internal sealed interface InputStream : AutoCloseable {
    fun advancePosition(places: Int)
    fun savePosition()
    fun revertPosition()
    fun removeSavedPosition()
    fun next(): Char
    fun peek(): Char
    fun hasNext(): Boolean

    /**
     * Thrown when an attempt is made to read from an input stream that has been exhausted.
     */
    object OutOfBoundsSignal : Throwable()
}

/**
 * An input stream backed by a string.
 */
internal class StringInputStream(private val input: String) : InputStream {
    private var position = 0

    /**
     * A stack of saved indices.
     */
    private val savedPositions = object {
        private var size: Int = 0
        private var data: IntArray = IntArray(size)

        operator fun get(index: Int) = data[index]

        operator fun plusAssign(n: Int) {
            if (size == data.size) {
                val new = IntArray(size * 2)
                data.copyInto(new)
                data = new
            }
            data[size] = n
            ++size
        }

        fun removeLast() = data[size - 1].also { --size }

        override fun toString() = data.toString()
    }

    override fun advancePosition(places: Int) {
        position += places
    }

    override fun savePosition() {
        savedPositions += position
    }

    override fun revertPosition() {
        position = savedPositions.removeLast()
    }

    override fun removeSavedPosition() {
        savedPositions.removeLast()
    }

    override fun next() = peek().also { ++position }

    override fun peek() = checkBounds { input[position] }

    override fun hasNext() = position < input.length

    override fun close() { /* no-op */ }
}

/**
 * A buffered input stream backed by a [RawSource].
 */
internal class SourceInputStream(private val source: RawSource) : InputStream, AutoCloseable by source {
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
                throw InputStream.OutOfBoundsSignal
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