@file:Suppress("MemberVisibilityCanBePrivate")

package io.github.aeckar.parsing.containers

//  Mutable iterators typically unnecessary, and especially for performance-sensitive applications

private const val DEFAULT_SIZE = 10 // Default size of Java list
private const val GROWTH_FACTOR = 2

/**
 * A list of unboxed values.
 */
public interface ValueList {
    /**
     * The number of elements in this list.
     */
    public val size: Int
}

/**
 * A list of unboxed Booleans.
 */
public class BooleanList : ValueList, Iterable<Boolean> {
    /**
     * The last element in this list.
     * @throws IllegalStateException stack is empty
     */
    public var last: Boolean
        get() {
            check(size != 0) { "List is empty" }
            return buffer[size - 1]
        }
        set(value) {
            check(size != 0) { "List is empty" }
            buffer[size - 1] = value
        }

    private var buffer: BooleanArray = BooleanArray(DEFAULT_SIZE)

    override var size: Int = 0

    /**
     * Pops the last element from this list.
     * @return the former last element in this list
     * @throws IllegalStateException stack is empty
     */
    public fun removeLast(): Boolean = last.also { --size }

    /**
     * Returns the specified element.
     * @throws IndexOutOfBoundsException the element at the specified index does not exist
     */
    public operator fun get(index: Int): Boolean = buffer[index]

    /**
     * Pushes [element] to the end of this list.
     */
    public operator fun plusAssign(element: Boolean) {
        if (size == buffer.size) {
            val new = BooleanArray(size * GROWTH_FACTOR)
            buffer.copyInto(new)
            buffer = new
        }
        buffer[size++] = element
    }

    override fun iterator(): BooleanIterator = object : BooleanIterator() {
        var cursor = 0

        override fun hasNext() = cursor < size
        override fun nextBoolean() = buffer[cursor++]
    }

    override fun toString(): String = buffer.asSequence().take(size).joinToString(prefix = "[", postfix = "]")
}

/**
 * A list of unboxed integers.
 */
public class IntList : ValueList, Iterable<Int> {
    /**
     * The last element in this list.
     * @throws IllegalStateException stack is empty
     */
    public var last: Int
        get() {
            check(size != 0) { "List is empty" }
            return buffer[size - 1]
        }
        set(value) {
            check(size != 0) { "List is empty" }
            buffer[size - 1] = value
        }

    private var buffer: IntArray = IntArray(DEFAULT_SIZE)

    override var size: Int = 0

    /**
     * Pops the last element from this list.
     * @return the former last element in this list
     * @throws IllegalStateException stack is empty
     */
    public fun removeLast(): Int = last.also { --size }

    /**
     * Returns the specified element.
     * @throws IndexOutOfBoundsException the element at the specified index does not exist
     */
    public operator fun get(index: Int): Int = buffer[index]

    /**
     * Pushes [element] to the end of this list.
     */
    public operator fun plusAssign(element: Int) {
        if (size == buffer.size) {
            val new = IntArray(size * GROWTH_FACTOR)
            buffer.copyInto(new)
            buffer = new
        }
        buffer[size++] = element
    }

    override fun iterator(): IntIterator = object : IntIterator() {
        var cursor = 0

        override fun hasNext() = cursor < size
        override fun nextInt() = buffer[cursor++]
    }

    override fun toString(): String = buffer.asSequence().take(size).joinToString(prefix = "[", postfix = "]")
}

/**
 * A list of unboxed long integers.
 */
public class LongList : ValueList, Iterable<Long> {
    /**
     * The last element in this list.
     * @throws IllegalStateException stack is empty
     */
    public var last: Long
        get() {
            check(size != 0) { "List is empty" }
            return buffer[size - 1]
        }
        set(value) {
            check(size != 0) { "List is empty" }
            buffer[size - 1] = value
        }

    private var buffer: LongArray = LongArray(DEFAULT_SIZE)

    override var size: Int = 0

    /**
     * Pops the last element from this list.
     * @return the former last element in this list
     * @throws IllegalStateException stack is empty
     */
    public fun removeLast(): Long = last.also { --size }

    /**
     * Returns the specified element.
     * @throws IndexOutOfBoundsException the element at the specified index does not exist
     */
    public operator fun get(index: Int): Long = buffer[index]

    /**
     * Pushes [element] to the end of this list.
     */
    public operator fun plusAssign(element: Long) {
        if (size == buffer.size) {
            val new = LongArray(size * GROWTH_FACTOR)
            buffer.copyInto(new)
            buffer = new
        }
        buffer[size++] = element
    }

    override fun iterator(): LongIterator = object : LongIterator() {
        var cursor = 0

        override fun hasNext() = cursor < size
        override fun nextLong() = buffer[cursor++]
    }

    override fun toString(): String = buffer.asSequence().take(size).joinToString(prefix = "[", postfix = "]")
}

/**
 * A list of unboxed double-precision floats.
 */
public class DoubleList : ValueList, Iterable<Double> {
    /**
     * The last element in this list.
     * @throws IllegalStateException stack is empty
     */
    public var last: Double
        get() {
            check(size != 0) { "List is empty" }
            return buffer[size - 1]
        }
        set(value) {
            check(size != 0) { "List is empty" }
            buffer[size - 1] = value
        }

    private var buffer: DoubleArray = DoubleArray(DEFAULT_SIZE)

    override var size: Int = 0

    /**
     * Removes the last element from this list.
     * @return the former last element in this list
     * @throws IllegalStateException stack is empty
     */
    public fun removeLast(): Double = last.also { --size }

    /**
     * Returns the specified element.
     * @throws IndexOutOfBoundsException the element at the specified index does not exist
     */
    public operator fun get(index: Int): Double = buffer[index]

    /**
     * Pushes [element] to the end of this list.
     */
    public operator fun plusAssign(element: Double) {
        if (size == buffer.size) {
            val new = DoubleArray(size * GROWTH_FACTOR)
            buffer.copyInto(new)
            buffer = new
        }
        buffer[size++] = element
    }

    override fun iterator(): DoubleIterator = object : DoubleIterator() {
        var cursor = 0

        override fun hasNext() = cursor < size
        override fun nextDouble() = buffer[cursor++]
    }

    override fun toString(): String = buffer.asSequence().take(size).joinToString(prefix = "[", postfix = "]")
}