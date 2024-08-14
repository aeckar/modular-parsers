package io.github.aeckar.parsing.utils

/**
 * A function that takes one argument and returns nothing.
 */
public fun interface Consumer<ArgumentT> {
    public operator fun invoke(argument: ArgumentT)
}