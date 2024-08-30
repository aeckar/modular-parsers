package io.github.aeckar.parsing.utils

/**
 * Returns an iterator that gives this integer the given amount of [times].
 */
public fun Int.repeat(times: Int): IntIterator = object : IntIterator() {
    private var count = 0

    override fun hasNext() = count < times
    override fun nextInt() = this@repeat
}

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