package io.github.aeckar.parsing.containers

public inline fun <T> Iterable<T>.booleanSumOf(transform: (T) -> Boolean): Boolean {
    var isModified = false
    forEach { isModified = isModified || transform(it) }
    return isModified
}