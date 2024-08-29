package io.github.aeckar.parsing.utils

/**
 * A barebones implementation of a first-in-first-out integer stack.
 */
internal class IntStack {
    private var size: Int = 10
    private var data: IntArray = IntArray(size)

    fun last() = data[size - 1]

    fun incrementLast() {
        ++data[size]
    }

    fun removeLast(): Int = last().also { --size }

    operator fun get(index: Int): Int = data[index]

    operator fun plusAssign(n: Int) {
        if (size == data.size) {
            val new = IntArray(size * 2)
            data.copyInto(new)
            data = new
        }
        data[size] = n
        ++size
    }

    override fun toString(): String = data.toString()
}