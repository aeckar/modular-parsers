package io.github.aeckar.parsing.utils

import io.github.aeckar.parsing.MalformedParserException

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

    val ranges = mutableListOf<CharRange>()
    val chars = iterator()
    while (chars.hasNext()) {
        ranges += chars.nextRange()
    }
    return ranges
}

internal fun List<CharRange>.invertRanges(): List<CharRange> {
    val inverted = ArrayList<CharRange>(size)
    for (range in this) {
        if (range.first == Char.MIN_VALUE) {
            if (range.last == Char.MAX_VALUE) {
                throw MalformedParserException("Cannot invert all-inclusive switch range '\\u0000-\\uFFFF'")
            }
        } else {
            inverted += Char.MIN_VALUE..<range.first
            if (range.last != Char.MAX_VALUE) {
                continue
            }
        }
        inverted += (range.last + 1)..Char.MAX_VALUE
    }
    return inverted
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