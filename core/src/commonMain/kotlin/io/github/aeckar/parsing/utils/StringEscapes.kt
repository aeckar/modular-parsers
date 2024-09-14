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

// ------------------------------ color escapes ------------------------------

// Input substrings
internal fun String.yellow() = "\u001B[33m$this\u001B[0m"

// Symbols
internal fun String.blue() = "\u001B[34m$this\u001B[0m"

// Debug message location
internal fun String.grey() = "\u001B[90m$this\u001B[0m"

// "Match failed"
internal fun String.redEmphasis() = "\u001B[1;4;31m$this\u001B[0m"

// "Match succeeded"
internal fun String.greenEmphasis() = "\u001B[1;4;32m$this\u001B[0m"

// Skip behavior
internal fun String.magentaBold() = "\u001B[1;35m$this\u001B[0m"