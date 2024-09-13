package io.github.aeckar.parsing.containers

/**
 * A revertible iterator over a sequence of elements, each of which is assigned some object.
 */
public interface PivotIterator<out E, out P, out H> : RevertibleIterator<E, P> {
    /**
     * Returns the object assigned to the current element.
     */
    public fun here(): H
}

/**getNode
 * An iterator pivoting over a sequence of characters.
 */
public interface CharPivotIterator<out P, out H> : PivotIterator<Char, P, H>, CharRevertibleIterator<P>

/**
 * An iterator pivoting over a sequence of indexable elements.
 */
internal abstract class AbstractPivotIterator<out E, P : Comparable<P>, out H> internal constructor(
    private val revertible: RevertibleIterator<E, P>,
    private val init: () -> H
) : PivotIterator<E, P, H>, RevertibleIterator<E, P> {
    private var cursor: Pivot<H, P>? = null

    final override fun here(): H {
        val node = cursor.findOrInsert(revertible.position()) { Pivot(revertible.position(), init()) }
        this.cursor = node
        return node.value
    }
}