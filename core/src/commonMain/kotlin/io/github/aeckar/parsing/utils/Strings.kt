@file:JvmName("Escapes")
package io.github.aeckar.parsing.utils

import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.jvm.JvmName

/**
 * Contains all "hard" (unconditional) Kotlin keywords.
 *
 * Taken from https://kotlinlang.org/docs/keyword-reference.html#hard-keywords.
 */
public val kotlinHardKeywords: Set<String> = persistentSetOf(
    "as",       "break",
    "class",    "continue",
    "do",       "else",
    "false",    "for",
    "fun",      "if",
    "in",       "interface",
    "null",     "object",
    "package",  "return",
    "super",    "this",
    "throw",    "true",
    "try",      "typealias",
    "typeof",   "val",
    "var",      "when",
    "while"
)

/**
 * A list of character ranges that a character must satisfy to be
 * the first character in a [Kotlin identifier][isKotlinIdentifier].
 */
public val kotlinIdentifierStart: List<CharRange> = "a-zA-Z_".toRanges().toImmutableList()

/**
 * A list of character ranges that a character must satisfy to be
 * any character besides the first one in a [Kotlin identifier][isKotlinIdentifier].
 */
public val kotlinIdentifierPart: List<CharRange> = "a-ZA-Z0-9_".toRanges().toImmutableList()

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

/**
 * Returns true if this contains only a valid Kotlin identifier.
 */
public fun String.isKotlinIdentifier(): Boolean {
    return this.isNotEmpty()
            && this !in kotlinHardKeywords
            && this[0] satisfies kotlinIdentifierStart
            && takeLast(length - 1).all { it satisfies kotlinIdentifierPart }
}