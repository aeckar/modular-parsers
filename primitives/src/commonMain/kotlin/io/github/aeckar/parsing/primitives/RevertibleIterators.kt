package io.github.aeckar.parsing.primitives

import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readString

/**
 * A sequence of elements whose position can be saved and reverted to later.
 */
public interface RevertibleIterator<out E, out P> : Iterator<E> {
    /**
     * Advances the cursor pointing to the current element by the given number of places.
     * @throws IllegalArgumentException [places] is negative
     */
    public fun advance(places: Int)

    /**
     * Saves the current cursor position.
     *
     * Can be called more than once to save multiple positions, even if [isExhausted] is true.
     */
    public fun save()

    /**
     * Reverts the position of the cursor to the one that was last [saved][save],
     * and removes it from the set of saved positions.
     * @throws NoSuchElementException [save] has not been called prior
     */
    public fun revert()

    /**
     * Removes the cursor position last [saved][save] without reverting the current cursor position to it.
     * @throws NoSuchElementException [save] has not been called prior
     */
    public fun removeSave()

    /**
     * Returns the next element in the sequence without advancing the current cursor position.
     */
    public fun peek(): E

    /**
     * Returns true if no more elements can be read from this iterator
     * without reverting to a previously saved cursor position.
     *
     * Equal in value to `!`[hasNext]`.
     */
    public fun isExhausted(): Boolean = !hasNext()

    /**
     * Returns an object representing the current position of this iterator.
     */
    public fun position(): P
}

/**
 * A revertible iterator over a sequence of characters.
 */
public interface CharRevertibleIterator<out P> : RevertibleIterator<Char, P>, CharIterator {
    /**
     * Returns a [peek] of the next unboxed character.
     */
    public fun peekChar(): Char

    override fun peek(): Char = peekChar()
}

/**
 * A revertible iterator over an indexable sequence of elements.
 */
internal abstract class IndexRevertibleIterator<out E> : RevertibleIterator<E, Int> {
    /**
     * The index of the element being pointed to by the iterator.
     */
    protected var position = 0

    private val savedPositions = IntStack()

    final override fun advance(places: Int) {
        position += places
    }

    final override fun save() {
        savedPositions += position
    }

    final override fun revert() {
        position = savedPositions.removeLast()
    }

    final override fun removeSave() {
        savedPositions.removeLast()
    }

    final override fun position() = position
}

internal class ListRevertibleIterator<out E>(private val elements: List<E>) : IndexRevertibleIterator<E>() {
    override fun hasNext() = position < elements.size
    override fun isExhausted() = position >= elements.size
    override fun next(): E = peek().also { ++position }
    override fun peek(): E = elements[position]

    override fun toString() = buildString {
        append(if (isExhausted()) "<iterator exhausted>" else "${peek()}")
        append(" (index = $position)")
    }
}

internal class StringRevertibleIterator(
    private val chars: String
) : IndexRevertibleIterator<Char>(), CharRevertibleIterator<Int> {
    override fun hasNext() = position < chars.length
    override fun isExhausted() = position >= chars.length
    override fun nextChar() = peek().also { ++position }
    override fun peekChar() = chars[position]

    override fun toString() = buildString {
        append(if (isExhausted()) "<iterator exhausted>" else "'${peekChar()}'")
        append(" (index = $position)")
    }
}

internal class SourceRevertibleIterator(private val source: RawSource) : CharRevertibleIterator<SourcePosition> {
    private val buffer = mutableListOf<String>()
    private val savedPositions = mutableListOf<SourcePosition>()
    private var section = 0
    private var sectionPosition = 0

    override fun position() = SourcePosition(section, sectionPosition)

    override fun advance(places: Int) {
        sectionPosition += places
    }

    override fun save() {
        verifySection()
        savedPositions += position()
    }

    override fun revert() {
        savedPositions.removeLast().let { (section, position) ->
            this.section = section
            this.sectionPosition = position
        }
    }

    override fun removeSave() {
        savedPositions.removeLast()
    }

    override fun hasNext() = sectionPosition < buffer[section].length || loadSection()
    override fun nextChar() = peek().also { ++sectionPosition }

    override fun peekChar(): Char {
        verifySection()
        return buffer[section][sectionPosition]
    }

    override fun toString() = buildString {
//        val section = bufferSection.withEscapes()
//            .asIterable()
//            .joinToString(separator = "", prefix = "'", postfix = "'", limit = 20)
        append(if (isExhausted()) "<iterator exhausted>" else "'${peekChar()}'")
        append(" (absolute position = ${absolutePosition()})")
    }

    private fun verifySection() {
        while (sectionPosition >= buffer[section].length) {
            sectionPosition -= buffer[section].length
            if (!loadSection()) {
                throw NoSuchElementException(
                    "Underlying source is exhausted (absolute position = ${absolutePosition()})")
            }
            ++section
        }
    }

    /**
     * Returns true if the next section exists, or false if this input stream has been exhausted.
     */
    private fun loadSection(): Boolean {
        if (section < buffer.lastIndex) {
            return true
        }
        val nextSection = Buffer().apply { source.readAtMostTo(this, 8192L) }.readString()
            // Throws IllegalStateException when source is closed
        if (nextSection.isEmpty()) {
            return false
        }
        buffer += nextSection
        return true
    }

    private fun absolutePosition(): Int {
        if (section == 0) {
            return sectionPosition
        }
        return buffer.asSequence().take(section - 1).sumOf { it.length } + sectionPosition
    }
}