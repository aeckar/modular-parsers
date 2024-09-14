package io.github.aeckar.parsing.containers

/**
 * A revertible iterator over a sequence of elements, each of which is assigned some value.
 */
public interface PivotIterator<out E, P : Comparable<P>, out H> : RevertibleIterator<E, P> {
    /**
     * Returns the value assigned to the current element.
     */
    public fun here(): H

    /**
     * Returns a list of the previously visited pivots, including the current one.
     *
     * Invoking this function may incur a significant performance impact for large sequences.
     */
    public fun pivots(): List<Pivot<P, H>>
}

/**getNode
 * An iterator pivoting over a sequence of characters.
 */
public interface CharPivotIterator<P : Comparable<P>, out H> : PivotIterator<Char, P, H>, CharRevertibleIterator<P>

/**
 * An iterator pivoting over a sequence of indexable elements.
 */
internal abstract class AbstractPivotIterator<out E, P : Comparable<P>, out H> internal constructor(
    private val revertible: RevertibleIterator<E, P>,
    private val init: () -> H
) : PivotIterator<E, P, H>, RevertibleIterator<E, P> {
    private var cursor: Pivot<P, H>? = null

    final override fun here(): H {
        val node = cursor.findOrInsert(revertible.position()) { Pivot(revertible.position(), init()) }
        this.cursor = node
        return node.value
    }

    final override fun pivots(): List<Pivot<P, H>> = cursor.toList()

    // equals(), hashCode() implemented by revertible delegates
}