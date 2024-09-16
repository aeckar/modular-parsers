package io.github.aeckar.parsing.containers

/**
 * A sequence of characters providing unboxed access to each element.
 *
 * Interface equivalent of [kotlin.collections.CharIterator].
 */
public interface CharIterator : Iterator<Char> {
    /**
     * Returns the [next] unboxed character.
     */
    public fun nextChar(): Char

    override fun next(): Char = nextChar()
}