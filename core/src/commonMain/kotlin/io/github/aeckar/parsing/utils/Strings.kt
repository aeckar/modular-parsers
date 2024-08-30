package io.github.aeckar.parsing.utils

/**
 * Returns a string equal to this one with invisible characters expressed as their Kotlin escape character.
 */
internal fun String.withEscapes() = buildString {
    this@withEscapes.forEach {
        if (it.isWhitespace() || it.isISOControl()) {
            append("\\u${it.code.toString(16).padStart(4, '0')}")
        } else {
            append(it)
        }
    }
}