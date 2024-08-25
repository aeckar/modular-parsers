package io.github.aeckar.parsing.utils

/**
 * A sequence of elements whose position can be saved and reverted to later.
 */
public interface PivotIterator<ElementT> : Iterator<ElementT> {
    /**
     * Advances the cursor pointing to the current element by the given number of places.
     * @throws IllegalArgumentException [places] is negative
     */
    public fun advance(places: Int)

    /**
     * Saves the current cursor position.
     *
     * Can be called more than once to save multiple positions,
     * even if the iterator has been exhausted ([hasNext]` == false`)
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
    public fun peek(): ElementT
}

/**
 * An iterator pivoting over a sequence of characters.
 */
public interface CharPivotIterator : PivotIterator<Char> {
    /**
     * Returns the [next] unboxed character.
     */
    public fun nextChar(): Char

    /**
     * Returns a [peek] of the next unboxed character.
     */
    public fun peekChar(): Char

    override fun next(): Char = nextChar()
    override fun peek(): Char = peekChar()
}

/**
 * An iterator pivoting over a sequence of elements whose cursor position is represented by an integer.
 */
public abstract class SimplePivotIterator<T> : PivotIterator<T> {
    /**
     * The index of the element being pointed to by the iterator.
     */
    protected var position: Int = 0

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
}

