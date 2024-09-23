package io.github.aeckar.parsing.containers

/**
 * A sequence of characters providing unboxed access to each element.
 * @see kotlin.collections.CharIterator
 */
public interface CharIterator : Iterator<Char> {
    /**
     * Returns the [next] unboxed character.
     */
    public fun nextChar(): Char

    override fun next(): Char = nextChar()
}