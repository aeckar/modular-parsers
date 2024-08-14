package io.github.aeckar.parsing.utils

/**
 * A function that takes one argument and returns nothing.
 */
public fun interface Consumer<T> {
    public operator fun invoke(argument: T)
}