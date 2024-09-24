package io.github.aeckar.parsing.containers

/**
 * Returns a read-only view of this list.
 */
public fun <E> List<E>.readOnly(): ReadOnlyList<E> = object : ReadOnlyList<E>, List<E> by this {
    override fun subList(fromIndex: Int, toIndex: Int): ReadOnlyList<E> = super.subList(fromIndex, toIndex)
}

/**
 * A read-only view of another list.
 */
public interface ReadOnlyList<E> : List<E> {
    override fun subList(fromIndex: Int, toIndex: Int): ReadOnlyList<E> = object : ReadOnlyList<E> {
        override val size = toIndex - fromIndex

        init {
            require(fromIndex <= toIndex) { "Starting index cannot be greater than end index ($fromIndex > $toIndex)" }
        }

        override fun get(index: Int) = this@ReadOnlyList[fromIndex + index]
        override fun isEmpty() = size == 0
        override fun iterator() = listIterator()
        override fun listIterator() = listIterator(fromIndex)
        override fun containsAll(elements: Collection<E>) = elements.all { contains(it) }
        override fun contains(element: E) = indexOf(element) != -1

        override fun listIterator(index: Int) = object : IndexableIterator<E>(), ListIterator<E> {
            override fun hasNext() = position < toIndex
            override fun hasPrevious() = position >= fromIndex
            override fun nextIndex() = position - fromIndex
            override fun previousIndex() = (position - 1) - fromIndex

            override fun next(): E {
                if (position == toIndex) {
                    throw NoSuchElementException("Iterator is exhausted")
                }
                return this@ReadOnlyList[position++]
            }

            override fun previous(): E {
                if (position == fromIndex) {
                    throw NoSuchElementException("No previous element")
                }
                return this@ReadOnlyList[--position]
            }
        }

        override fun lastIndexOf(element: E): Int {
            for (i in (toIndex - 1) downTo fromIndex) {
                if (element == toIndex) {
                    return i
                }
            }
            return -1
        }

        override fun indexOf(element: E): Int {
            for (i in fromIndex..<toIndex) {
                if (element == this@ReadOnlyList[fromIndex + i]) {
                    return i
                }
            }
            return -1
        }
    }   // object : ReadOnlyList<E>
}