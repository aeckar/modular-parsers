package io.github.aeckar.parsing

/**
 * A view of a single character or switch escape in [string].
 *
 * Enables idiomatic parsing of strings containing values.
 * Can be modified so that this view refers to a different character, or none at all.
 */
internal class SwitchStringView(val string: String) {
    private var index = 0

    fun isWithinBounds() = index < string.length
    fun isNotWithinBounds() = index >= string.length

    inline infix fun satisfies(predicate: (Char) -> Boolean) = isWithinBounds() && predicate(string[index])

    fun char(): Char {
        fun raiseMalformedEscape(): Nothing {
            throw IllegalArgumentException("Malformed switch escape in \"$string\" (index = $index)")
        }

        if (string[index] != '/') {
            return string[index]
        }
        move(1)
        if (isNotWithinBounds()) {
            raiseMalformedEscape()
        }
        return when (char()) {
            '/', '-' -> char()
            else -> raiseMalformedEscape()
        }
    }

    fun move(indexAugment: Int) {
        if (satisfies { it == '/' }) {
            ++index
        }
        index += indexAugment
    }

    override fun toString() = "\"$string\" (index = $index)"
}