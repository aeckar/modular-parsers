@file:Suppress("MemberVisibilityCanBePrivate")

package io.github.aeckar.parsing.containers

private const val INITIAL_SIZE = 10

/**
 * A list of unboxed boolean values.
 */
public interface NumberList {
    /**
     * The number of elements in this list.
     */
    public val size: Int
}

/**
 * A list of unboxed Booleans.
 */
public class BooleanList : NumberList, Iterable<Boolean> {
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

    private var buffer: BooleanArray = BooleanArray(INITIAL_SIZE)

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
            val new = BooleanArray(size * 2)
            buffer.copyInto(new)
            buffer = new
        }
        buffer[size++] = element
    }

    override fun iterator(): BooleanIterator = buffer.iterator()
    override fun toString(): String = buffer.asSequence().take(size).joinToString(prefix = "[", postfix = "]")
}

/**
 * A list of unboxed integers.
 */
public class IntList : NumberList, Iterable<Int> {
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

    private var buffer: IntArray = IntArray(INITIAL_SIZE)

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
            val new = IntArray(size * 2)
            buffer.copyInto(new)
            buffer = new
        }
        buffer[size++] = element
    }

    override fun iterator(): IntIterator = buffer.iterator()
    override fun toString(): String = buffer.asSequence().take(size).joinToString(prefix = "[", postfix = "]")
}

/**
 * A list of unboxed long integers.
 */
public class LongList : NumberList, Iterable<Long> {
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

    private var buffer: LongArray = LongArray(INITIAL_SIZE)

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
            val new = LongArray(size * 2)
            buffer.copyInto(new)
            buffer = new
        }
        buffer[size++] = element
    }

    override fun iterator(): LongIterator = buffer.iterator()
    override fun toString(): String = buffer.asSequence().take(size).joinToString(prefix = "[", postfix = "]")
}

/**
 * A list of unboxed double-precision floats.
 */
public class DoubleList : NumberList, Iterable<Double> {
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

    private var buffer: DoubleArray = DoubleArray(INITIAL_SIZE)

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
            val new = DoubleArray(size * 2)
            buffer.copyInto(new)
            buffer = new
        }
        buffer[size++] = element
    }

    override fun iterator(): DoubleIterator = buffer.iterator()
    override fun toString(): String = buffer.asSequence().take(size).joinToString(prefix = "[", postfix = "]")
}