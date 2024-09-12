package io.github.aeckar.parsing.primitives

import kotlin.jvm.JvmInline

private const val INITIAL_SIZE = 10

/**
 * A first-in-first-out stack of unboxed values.
 */
public interface Stack {
    /**
     * The number of elements in this stack.
     */
    public val size: Int

    /**
     * Returns this stack as a stack of boolean values.
     * 
     * If this stack is already made of boolean values, a copy is returned.
     */
    public fun toBooleanStack(): BooleanStack

    /**
     * Returns this stack as a stack of integers.
     *
     * If this stack is already made of integers, a copy is returned.
     */
    public fun toIntStack(): IntStack
}

/**
 * A stack of integers.
 */
public class IntStack private constructor(
    override var size: Int,
    buffer: IntArray
) : Stack, Iterable<Int> {
    @PublishedApi
    internal var buffer: IntArray = buffer  // Disallow random access
        private set

    /**
     * Returns an empty stack.
     */
    public constructor() : this(0, IntArray(INITIAL_SIZE))

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

    override fun toBooleanStack(): BooleanStack = BooleanStack(toIntStack())

    override fun toIntStack(): IntStack = IntStack(size, buffer.copyOf())

    override fun iterator(): Iterator<Int> = buffer.iterator()
    override fun toString(): String = buffer.take(size).toString()
}

/**
 * A stack of boolean values
 */
@JvmInline
public value class BooleanStack internal constructor(
    @PublishedApi internal val ints: IntStack
) : Stack by ints, Iterable<Boolean> {
    /**
     * Returns an empty stack.
     */
    public constructor() : this(IntStack())

    /**
     * Returns the top of the stack.
     */
    public fun last(): Boolean = ints.last() == 1

    /**
     * Modifies the top of the stack according to [action].
     */
    public inline fun mapLast(action: (Boolean) -> Boolean) {
        ints += if (action(ints.removeLast() == 1)) 1 else 0
    }

    /**
     * Pops the top element from the stack and returns its value.
     */
    public fun removeLast(): Boolean = last().also { --ints.size }

    /**
     * Pushes [bool] to the top of the stack.
     */
    public operator fun plusAssign(bool: Boolean) {
        ints += if (bool) 1 else 0
    }

    override fun iterator(): BooleanIterator {
        val buffer = BooleanArray(ints.size)
        repeat(ints.size) { buffer[it] = ints.buffer[it] == 1 }
        return buffer.iterator()
    }

    override fun toString(): String = ints.asSequence().map { it == 1 }.joinToString(prefix = "[", postfix = "]")
}