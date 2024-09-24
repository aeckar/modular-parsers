package io.github.aeckar.parsing.containers

/**
 * Returns a removable object referring to the current object pointed to by this iterator.
 */
public fun MutableIterator<*>.asRemovable() = object : Removable by this {}

/**
 * An sequence of elements that returns a mutable iterator.
 */
public interface MutableIterable<T> : Iterable<T> {
    override fun iterator(): MutableIterator<T>
}