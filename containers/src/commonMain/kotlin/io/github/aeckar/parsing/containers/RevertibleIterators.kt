package io.github.aeckar.parsing.containers

import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readString

private const val SECTION_SIZE = 8192L

private fun raiseExhausted(): Nothing {
    throw NoSuchElementException("Iterator is exhausted")
}

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
     * Can be called more than once to save multiple positions, and even if [isExhausted] is true.
     */
    public fun save()

    /**
     * Reverts the position of the cursor to the one that was last [saved][save],
     * and removes it from the set of saved positions.
     * @throws IllegalStateException [save] has not been called prior
     */
    public fun revert()

    /**
     * Removes the cursor position last [saved][save] without reverting the current cursor position to it.
     * @throws IllegalStateException [save] has not been called prior
     */
    public fun removeSave()

    /**
     * Returns the next element in the sequence without advancing the current cursor position.
     * @throws NoSuchElementException the iterator is exhausted
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

    /**
     * Returns true if [other] is a revertible iterator over the same instance at the same position as this one.
     */
    override fun equals(other: Any?): Boolean
}

/**
 * A revertible iterator over a sequence of characters.
 */
public interface CharRevertibleIterator<out P> : RevertibleIterator<Char, P>, CharIterator {
    /**
     * Returns a [peek] of the next unboxed character.
     * @throws NoSuchElementException the iterator is exhausted
     */
    public fun peekChar(): Char

    override fun peek(): Char = peekChar()
}

/**
 * A revertible iterator over an indexable sequence of elements.
 */
internal abstract class IndexRevertibleIterator<out E> : RevertibleIterator<E, Int> {
    protected abstract val elements: Any?

    /**
     * The index of the element being pointed to by the iterator.
     */
    protected var position = 0

    private val savedPositions = IntList()

    final override fun advance(places: Int) {
        position += places
    }

    final override fun save() {
        savedPositions += position
    }

    final override fun revert() {
        position = removeLastSave()
    }

    final override fun removeSave() {
        removeLastSave()
    }

    final override fun position() = position

    final override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is IndexRevertibleIterator<*>) {
            return false
        }
        return elements === other.elements && position == other.position
    }

    final override fun hashCode(): Int {
        var result = elements.hashCode()
        result = 31 * result + position
        return result
    }

    final override fun toString(): String {
        val message = if (isExhausted()) "<iterator exhausted>" else "${peek()}"
        return "$message (index = $position)"
    }

    private fun removeLastSave(): Int {
        return try {
            savedPositions.removeLast()
        } catch (_: NoSuchElementException) {
            error("No positions saved")
        }
    }
}

internal class ListRevertibleIterator<out E>(override val elements: List<E>) : IndexRevertibleIterator<E>() {
    override fun hasNext() = position < elements.size
    override fun isExhausted() = position >= elements.size
    override fun next(): E = peek().also { ++position }

    override fun peek(): E {
        return try {
            elements[position]
        } catch (_: IndexOutOfBoundsException) {
            raiseExhausted()
        }
    }
}

internal class StringRevertibleIterator(
    override val elements: String
) : IndexRevertibleIterator<Char>(), CharRevertibleIterator<Int> {
    override fun hasNext() = position < elements.length
    override fun isExhausted() = position >= elements.length
    override fun nextChar() = peekChar().also { ++position }

    override fun peekChar(): Char {
        return try {
            elements[position]
        } catch (_: IndexOutOfBoundsException) {
            raiseExhausted()
        }
    }
}

internal class SourceRevertibleIterator(private val source: RawSource) : CharRevertibleIterator<SourcePosition> {
    private val buffer = mutableListOf("")  // Allows initial hasNext()
    private val savedPositions = mutableListOf<SourcePosition>()
    private var section = 0
    private var sectionPosition = 0

    override fun position() = SourcePosition(section, sectionPosition)

    override fun advance(places: Int) {
        sectionPosition += places
    }

    override fun save() {
        savedPositions += position()
    }

    override fun revert() {
        removeLastSave().let { (section, position) ->
            this.section = section
            this.sectionPosition = position
        }
    }

    override fun removeSave() {
        removeLastSave()
    }

    override fun hasNext() = sectionPosition < buffer[section].length || loadSection()
    override fun nextChar() = peekChar().also { ++sectionPosition }

    override fun peekChar(): Char {
        verifyPosition()
        return buffer[section][sectionPosition]
    }

    override fun toString(): String {
        val message = if (isExhausted()) "<iterator exhausted>" else "'${peekChar()}'"
        return message + " (position = ${absolutePosition()})"
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is SourceRevertibleIterator) {
            return false
        }
        return source === other.source && section == other.section && sectionPosition == other.sectionPosition
    }

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + section
        result = 31 * result + sectionPosition
        return result
    }

    private fun removeLastSave(): SourcePosition {
        return try {
            savedPositions.removeLast()
        } catch (_: NoSuchElementException) {
            error("No positions saved")
        }
    }

    /**
     * Returns true if the next section exists, or false if this input stream has been exhausted.
     */
    private fun loadSection(): Boolean {
        if (section < buffer.lastIndex) {
            return true
        }
        val nextSection = Buffer().apply { source.readAtMostTo(this, SECTION_SIZE) }.readString()
            // Throws IllegalStateException when source is closed
        if (nextSection.isEmpty()) {
            return false
        }
        buffer += nextSection
        return true
    }

    private fun verifyPosition() {
        while (sectionPosition >= buffer[section].length) {
            sectionPosition -= buffer[section].length
            if (!loadSection()) {
                throw NoSuchElementException("Source is exhausted at position ${absolutePosition()}")
            }
            ++section
        }
    }

    private fun absolutePosition(): Int {
        if (section == 0) {
            return sectionPosition
        }
        return buffer.asSequence().take(section - 1).sumOf { it.length } + sectionPosition
    }
}