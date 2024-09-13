package io.github.aeckar.parsing.utils

import io.github.aeckar.parsing.MalformedParserException
import io.github.aeckar.parsing.Switch
import io.github.aeckar.parsing.containers.revertibleIterator

/**
 * Returns true if this is in any member of [ranges].
 */
public infix fun Char.satisfies(ranges: List<CharRange>): Boolean = ranges.any { this in it }

/**
 * Returns a list of character ranges that can used by [Switch] symbols.
 * @see rangesToString
 */
internal fun String.toRanges(): MutableList<CharRange> {
    fun CharIterator.nextCharOrEscape(): Char {
        try {
            val char = nextChar()
            return if (char != '/') {
                char
            } else when (val escape = nextChar()) {
                '-', '/' -> escape
                else -> throw MalformedParserException("Invalid switch literal escape character '$escape'")
            }
        } catch (_: NoSuchElementException) {
            throw MalformedParserException("Malformed switch literal '$this@toRanges'")
        }
    }

    fun CharIterator.nextRange(): List<CharRange> {
        val start = nextCharOrEscape()
        if (start == '-') {
            return listOf(Char.MIN_VALUE..nextCharOrEscape())
        }
        if (!hasNext()) {
            return listOf(start..start)
        }
        val next = nextCharOrEscape()
        return if (next != '-') {
            listOf(start..start, next..next)
        } else {
            val endInclusive = if (!hasNext()) Char.MAX_VALUE else nextCharOrEscape()
            listOf(start..endInclusive)
        }
    }

    if (this.isEmpty()) {
        throw MalformedParserException("Empty string cannot be used as a switch literal")
    }
    if (this == "-") {
        return mutableListOf(Char.MIN_VALUE..Char.MAX_VALUE)
    }
    val ranges = mutableListOf<CharRange>()
    val chars = iterator()
    while (chars.hasNext()) {
        ranges += chars.nextRange()
    }
    return ranges
}

/**
 * Inverts the ranges and merges intersecting bounds in this list, in place.
 * @return this list
 */
internal fun MutableList<CharRange>.invertRanges(): List<CharRange> {
    val unoptimized = ArrayList<CharRange>(size)
    sortBy { it.first }
    for (range in this) {   // 1. Invert each range
        if (range.first == Char.MIN_VALUE) {
            if (range.last == Char.MAX_VALUE) {
                throw MalformedParserException("Cannot invert all-inclusive switch range '\\u0000-\\uFFFF'")
            }
            // fall-through
        } else {
            unoptimized += Char.MIN_VALUE..<range.first
            if (range.last == Char.MAX_VALUE) {
                continue
            }
        }
        unoptimized += (range.last + 1)..Char.MAX_VALUE
    }
    clear()
    // First lower bound is always Char.MIN_VALUE
    // Last upper bound is always Char.MAX_VALUE
    val ranges = unoptimized.revertibleIterator()
    for (range in ranges) {   // 2. Ensure inverted bounds do not overlap
        if (ranges.hasNext() && range.last > ranges.peek().first) {
            add(range.first..ranges.next().last)    // Consume next range
        } else {
            add(range)
        }
    }
    return this
}

internal fun List<CharRange>.rangesToString() = joinToString("") {
    if (it.first == it.last) it.first.toString() else "${it.first}-${it.last}"
}

/**
 * Merges intersecting ranges in the list, in place.
 * @return a view of a portion of this list
 */
internal fun MutableList<CharRange>.optimizeRanges(): List<CharRange> {
    if (size == 1) {
        return this
    }
    var lastMerged = 0
    sortBy { it.first }
    for (i in 1..lastIndex) {
        if (this[i].first <= this[lastMerged].last) {
            this[lastMerged] = this[lastMerged].first..this[i].last
        } else {
            ++lastMerged
            this[lastMerged] = this[i]
        }
    }
    return subList(0, lastMerged + 1)
}