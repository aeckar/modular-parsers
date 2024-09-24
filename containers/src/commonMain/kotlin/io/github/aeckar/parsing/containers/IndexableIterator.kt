package io.github.aeckar.parsing.containers

/**
 * A basic iterator containing the current index, starting from 0.
 */
public abstract class IndexableIterator<out T> : Iterator<T> {
    /**
     * The index of the element being pointed to by the iterator.
     */
    protected var position: Int = 0
}