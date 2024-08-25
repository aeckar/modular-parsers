package io.github.aeckar.parsing.utils

import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readString

private data class RawSourcePosition(val bufferSection: String, val sectionPosition: Int)

/**
 * Returns an iterator pivoting over the elements in the list.
 */
public fun <T> List<T>.pivotIterator(): PivotIterator<T> = object : SimplePivotIterator<T>() {
    override fun hasNext(): Boolean = position < size
    override fun next(): T = peek().also { ++position }
    override fun peek(): T = this@pivotIterator[position]
}

/**
 * An input stream backed by a string.
 */
public fun String.pivotIterator(): CharPivotIterator = object : SimplePivotIterator<Char>(), CharPivotIterator {
    override fun hasNext() = position < length
    override fun nextChar() = peek().also { ++position }
    override fun peekChar() = this@pivotIterator[position]
}

/**
 * An iterator pivoting over the characters loaded from this source.
 *
 * If this is [closed][RawSource.close],
 * any function called from the returned instance throws an [IllegalStateException].
 */
public fun RawSource.pivotIterator(): CharPivotIterator = object : CharPivotIterator {
    private val buffer = mutableListOf<String>()
    private val savedPositions = mutableListOf<RawSourcePosition>()
    private var bufferSection = ""
    private var sectionPosition = 0

    override fun advance(places: Int) {
        sectionPosition += places
    }

    override fun save() {
        verifyBufferAndPosition()
        savedPositions += RawSourcePosition(bufferSection, sectionPosition)
    }

    override fun revert() {
        savedPositions.removeLast().let { (section, position) ->
            bufferSection = section
            sectionPosition = position
        }
    }

    override fun removeSave() {
        savedPositions.removeLast()
    }

    override fun hasNext() = sectionPosition < bufferSection.length || loadNextSection()

    override fun nextChar() = peek().also { ++sectionPosition }

    override fun peekChar(): Char {
        verifyBufferAndPosition()
        return bufferSection[sectionPosition]
    }

    private fun verifyBufferAndPosition() {
        while (sectionPosition >= bufferSection.length) {
            if (!loadNextSection()) {
                throw NoSuchElementException(
                        "Underlying source is exhausted (section = $bufferSection, position = $sectionPosition)")
            }
            sectionPosition -= bufferSection.length
            bufferSection = buffer.last()
        }
    }

    /**
     * Returns true if the next section exists, or false if this input stream has been exhausted.
     */
    private fun loadNextSection(): Boolean {
        val nextSection = Buffer().apply { this@pivotIterator.readAtMostTo(this, 8192L) }.readString()
        if (nextSection.isEmpty()) {
            return false
        }
        buffer += nextSection
        return true
    }
}