package io.github.aeckar.parsing.utils

/**
 * Ensures lists accessed by the user cannot be cast to a mutable list type in Kotlin code.
 *
 * Avoids the performance cost of copying to an immutable collection.
 */
internal class ImmutableList<E>(mutable: List<E>) : List<E> by mutable
