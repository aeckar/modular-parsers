package io.github.aeckar.parsing.containers

import kotlin.jvm.JvmInline

private val EMPTY_MULTISET = object : ReadOnlyMultiSet<Any?> {
    override val size = 0

    override fun isEmpty() = true
    override fun containsAll(elements: Collection<Any?>) = elements.isEmpty()
    override fun contains(element: Any?) = false
    override fun count(element: Any?) = 0

    override fun iterator() = object : Iterator<Any?> {
        override fun hasNext() = false
        override fun next() = throw NoSuchElementException("Multiset is empty")
    }
}

// ------------------------------ factories ------------------------------

// Type-safe builders (buildMultiSet...) made obsolete by read-only views

/**
 * Returns a read-only view over this set.
 */
public fun <E> Set<E>.readOnly(): ReadOnlySet<E> = object : ReadOnlySet<E> {
    override val size get() = this@readOnly.size

    override fun isEmpty() = size == 0
    override fun containsAll(elements: Collection<E>) = this@readOnly.containsAll(elements)
    override fun contains(element: E) = this@readOnly.contains(element)
    override fun iterator() = object : Iterator<E> by this@readOnly.iterator() {}   // Override mutable iterators
}

/**
 * Returns a read-only view over this multiset.
 */
public fun <E> MultiSet<E>.readOnly(): ReadOnlyMultiSet<E> {
    return object : ReadOnlyMultiSet<E>, MultiSet<E> by this {}
}

/**
 * Returns a read-only multiset containing the given elements.
 */
public fun <E> multiSetOf(vararg elements: E): ReadOnlyMultiSet<E> {

}

/**
 *
 */
public fun <E> mutableMultiSetOf(vararg elements: E): MutableMultiSet<E> {

}

/**
 *
 */
public fun <E> mutableMultiSetOf(): MutableMultiSet<E> {

}

/**
 * Returns an empty, read-only multiset.
 */
@Suppress("UNCHECKED_CAST")
public fun <E> emptyMultiSet(): ReadOnlyMultiSet<E> = EMPTY_MULTISET as ReadOnlyMultiSet<E>

// ------------------------------ interfaces ------------------------------

/**
 * A read-only view over another set.
 */
public interface ReadOnlySet<E> : Set<E>

/**
 * A set capable of containing the same value multiple times.
 * Also called a *bag*.
 *
 * Because duplicated elements are equivalent, they are discarded after they are counted.
 * To preserve duplicated elements, a set of lists should be used instead.
 */
public interface MultiSet<E> : Set<E> {
    /**
     * Returns the number of times this element was inserted into this set.
     */
    public fun count(element: E): Int
}

/**
 * A read-only view over another multiset.
 */
public interface ReadOnlyMultiSet<E> : MultiSet<E>, ReadOnlySet<E>

/**
 * A mutable multiset.
 */
public interface MutableMultiSet<E> : MultiSet<E>, MutableSet<E>

// ------------------------------ implementations ------------------------------

@JvmInline
private value class HashTable private constructor(private val array: Array<Any?>) {
    val capacity get() = array.size

    constructor(initialSize: Int) : this(Array<Any?>(initialSize) { NULL_ELEMENT })

    /**
     * Returns false if the given element is a duplicate.
     */
    fun hash(element: Any?): Boolean {
        var index = element.hashCode() % capacity
        while (array[index] !== NULL_ELEMENT) {
            if (array[index] == element) {
                return false
            }
            ++index
        }
        array[index] = element
        return true
    }

    fun grow(): HashTable {
        val new = HashTable(capacity * GROWTH_FACTOR)
        array.asSequence()
            .filter { it !== NULL_ELEMENT }
            .forEach(new::hash)
        return new
    }

    operator fun contains(element: Any?): Boolean {

    }

    private companion object {
        const val GROWTH_FACTOR = 2
        val NULL_ELEMENT = Any()
    }
}


/**
 * A multiset backed by an array.
 */
public class ArrayMultiSet<E>(
    initialSize: Int = DEFAULT_SIZE,
    private val loadFactor: Double = DEFAULT_LOAD_FACTOR
) : MutableMultiSet<E> {
    override val size: Int = 0

    private var table = HashTable(initialSize)

    override fun isEmpty(): Boolean = size == 0
    override fun containsAll(elements: Collection<E>): Boolean = elements.all { it in table }
    override fun contains(element: E): Boolean = element in table
    override fun removeAll(elements: Collection<E>): Boolean = elements.booleanSumOf(table::remove)
    override fun remove(element: E): Boolean = table.remove(element)
    override fun addAll(elements: Collection<E>): Boolean = elements.booleanSumOf(table::hash)

    override fun count(element: E): Int {
        TODO("Not yet implemented")
    }

    override fun iterator(): MutableIterator<E> {
        TODO("Not yet implemented")// blah
    }

    override fun clear() {
        table = HashTable(DEFAULT_SIZE)
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        // mutable iterator, remove if element (it) not at position
    }

    override fun add(element: E): Boolean {
        verifyCapacity()
        return table.hash(element)
    }

    private fun verifyCapacity() {
        if (size / table.capacity <= loadFactor) {
            return
        }
        table = table.grow()
    }

    private companion object {  // Default values according to Java
        const val DEFAULT_SIZE = 16
        const val DEFAULT_LOAD_FACTOR = 0.75
    }
}