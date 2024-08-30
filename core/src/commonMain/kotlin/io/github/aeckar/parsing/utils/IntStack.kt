package io.github.aeckar.parsing.utils

private const val INITIAL_SIZE = 10

/**
 * A first-in-first-out stack of unboxed integers.
 */
public class IntStack : Iterable<Int> {
    /**
     * The number of elements in this stack.
     */
    public var size: Int = 0
        private set

    @PublishedApi
    internal var buffer: IntArray = IntArray(INITIAL_SIZE)
        private set

    /**
     * Returns the top of the stack.
     */
    public fun last(): Int {
        ensureNotEmpty()
        return buffer[size - 1]
    }

    /**
     * Modifies the top of the stack according to [action].
     */
    public inline fun mapLast(action: (Int) -> Int) {
        ensureNotEmpty()
        buffer[size - 1] = action(buffer[size - 1])
    }

    /**
     * Pops the top element from the stack and returns its value.
     */
    public fun removeLast(): Int = last().also { --size }

    /**
     * Pushes [n] to the top of the stack.
     */
    public operator fun plusAssign(n: Int) {
        if (size == buffer.size) {
            val new = IntArray(size * 2)
            buffer.copyInto(new)
            buffer = new
        }
        buffer[size++] = n
    }

    @PublishedApi
    internal fun ensureNotEmpty() {
        if (size == 0) {
            throw NoSuchElementException("Stack is empty")
        }
    }

    override fun iterator(): IntIterator = object : IntIterator() {
        private var position = 0

        override fun hasNext() = position < size
        override fun nextInt() = buffer[position++]
    }

    override fun toString(): String = buffer.take(size).toString()
}