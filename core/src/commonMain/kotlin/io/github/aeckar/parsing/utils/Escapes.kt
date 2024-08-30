@file:JvmName("Escapes")
package io.github.aeckar.parsing.utils

import kotlin.jvm.JvmName

/**
 * Returns a string equal to this one with invisible characters expressed as their Kotlin escape character.
 */
public fun String.withEscapes(): String = buildString {
    this@withEscapes.forEach {
        if (it.isWhitespace() || it.isISOControl()) {
            append("\\u${it.code.toString(16).padStart(4, '0')}")
        } else {
            append(it)
        }
    }
}