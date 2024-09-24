package io.github.aeckar.parsing.containers

internal inline fun <R> checkBounds(access: () -> R): R {
    return try {
        access()
    } catch (_: IndexOutOfBoundsException) {
        throw NoSuchElementException("Iterator is exhausted")
    }
}