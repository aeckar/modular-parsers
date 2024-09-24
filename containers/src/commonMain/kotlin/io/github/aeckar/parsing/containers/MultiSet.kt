package io.github.aeckar.parsing.containers

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

    override fun containsAll(elements: Collection<E>) = this@readOnly.containsAll(elements)
    override fun contains(element: E) = this@readOnly.contains(element)
    override fun iterator() = object : Iterator<E> by this@readOnly.iterator() {}   // Override mutable iterators

    @Suppress("ReplaceSizeZeroCheckWithIsEmpty")
    override fun isEmpty() = size == 0
}

/**
 * Returns a read-only view over this multiset.
 */
public fun <E> MultiSet<E>.readOnly(): ReadOnlyMultiSet<E> {
    return object : ReadOnlyMultiSet<E>, MultiSet<E> by this {}
}

/**
 * Returns an empty, read-only multiset.
 */
@Suppress("UNCHECKED_CAST")
public fun <E> emptyMultiSet(): ReadOnlyMultiSet<E> = EMPTY_MULTISET as ReadOnlyMultiSet<E>

/**
 * Returns an empty, read-only multiset.
 *
 * Equivalent to calling [emptyMultiSet].
 */
public fun <E> multiSetOf(): ReadOnlyMultiSet<E> = emptyMultiSet()

/**
 * Returns a read-only multiset containing the given elements.
 */
public fun <E> multiSetOf(vararg elements: E): ReadOnlyMultiSet<E> = mutableMultiSetOf(*elements).readOnly()

/**
 * Returns an empty, mutable multiset.
 */
public fun <E> mutableMultiSetOf(): MutableMultiSet<E> = ArrayMultiSet<E>()

/**
 * Returns a mutable multiset containing the given elements.
 */
public fun <E> mutableMultiSetOf(vararg elements: E): MutableMultiSet<E> {
    val set = ArrayMultiSet<E>((elements.size * 1.25).toInt())
    elements.forEach(set::add)
    return set
}

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
 * A mutable multiset.
 */
public interface MutableMultiSet<E> : MultiSet<E>, MutableSet<E>, MutableIterable<E>

/**
 * A read-only view over another multiset.
 */
public interface ReadOnlyMultiSet<E> : MultiSet<E>, ReadOnlySet<E>

// ------------------------------ implementations ------------------------------

/**
 * A multiset backed by an array.
 */
public class ArrayMultiSet<E>(
    initialSize: Int = DEFAULT_SIZE,
    private val loadFactor: Double = DEFAULT_LOAD_FACTOR    // TODO find effective load factor for parsing
) : MutableMultiSet<E> {
    override val size: Int = 0

    private var table = Array<Any?>(initialSize) { ABSENT }
    private var counters = IntArray(initialSize)

    init {
        require(loadFactor > 0.0 && loadFactor <= 1.0) { "Invalid load factor: $loadFactor" }
    }

    override fun containsAll(elements: Collection<E>): Boolean = elements.all { it in this }
    override fun contains(element: E): Boolean = element == table[indexOfHash(element)]

    override fun removeAll(elements: Collection<E>): Boolean {
        elements.forEach { removeAt(indexOfHash(it)) }
        return elements.isNotEmpty()
    }

    override fun remove(element: E): Boolean {
        removeAt(indexOfHash(element))
        return true
    }

    @Suppress("ReplaceSizeZeroCheckWithIsEmpty")
    override fun isEmpty(): Boolean = size == 0

    override fun count(element: E): Int {
        val index = indexOfHash(element)
        return if (element != table[index]) 0 else counters[index]
    }

    private fun removeAt(index: Int) {
        checkBounds { table[index] = ABSENT }
        counters[index] = 0
    }

    override fun iterator(): MutableIterator<E> = object : IndexableIterator<E>(), MutableIterator<E> {
        init {
            moveToNext()
        }

        override fun remove() = removeAt(position)
        override fun hasNext() = position != table.size

        @Suppress("UNCHECKED_CAST")
        override fun next() = checkBounds {
            table[position].also { moveToNext() } as E
        }

        private fun moveToNext() {
            while (position < table.size && table[position] === ABSENT) { ++position }
        }
    }

    override fun clear() {
        table = Array(DEFAULT_SIZE) { ABSENT }
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        return mutableOrEach { value ->
            elements.none { it == value } implies ::remove
        }
    }

    override fun addAll(elements: Collection<E>): Boolean {
        verifyLoad(sizeAugment = elements.size)
        return elements.orEach(::hash)
    }

    override fun add(element: E): Boolean {
        verifyLoad(sizeAugment = 1)
        return hash(element)
    }

    private fun verifyLoad(sizeAugment: Int) {
        if ((size + sizeAugment) / table.size <= loadFactor) {
            return
        }
        val lastTable = table
        val lastCounters = counters
        val capacity = table.size * GROWTH_FACTOR
        table = Array<Any?>(capacity) { ABSENT }
        counters = IntArray(capacity)
        repeat(capacity) {
            val element = lastTable[it]
            if (element !== ABSENT) {
                rehash(element, lastCounters[it])
            }
        }
    }

    /**
     * Returns false if the given element is a duplicate.
     */
    private fun hash(element: Any?): Boolean {
        var index = element.hashCode() % table.size
        try {
            while (table[index] !== ABSENT) {
                if (table[index] == element) {
                    return false
                }
                ++index
            }
            table[index] = element
            return true
        } finally {
            ++counters[index]
        }
    }

    private fun indexOfHash(element: Any?): Int {
        var index = element.hashCode() % table.size
        while (table[index] !== ABSENT) {
            ++index
        }
        return index
    }

    private fun rehash(element: Any?, count: Int) {
        var index = indexOfHash(element)
        table[index] = element
        counters[index] = count
    }

    private companion object {  // Default values according to Java
        const val DEFAULT_SIZE = 16
        const val DEFAULT_LOAD_FACTOR = 0.75
        const val GROWTH_FACTOR = 2
        val ABSENT = Any()
    }
}