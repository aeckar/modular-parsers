package io.github.aeckar.parsing.containers

public fun <P : Comparable<P>, H> pivot(): (Pair<P, H>) -> Pivot<P, H> = { Pivot(it.first, it.second) }

/**
 * An object containing a position and a value.
 * @param position the location of this in some larger object
 * @param value a value specific to the location of this
 */
public data class Pivot<P : Comparable<P>, out H>(
    public val position: P,
    public val value: H
) : ListNode<Pivot<P, @UnsafeVariance H>>()

/**
 * Returns the pivot whose position has a [total ordering][Comparable] equal to the one given.
 *
 * If one does not exist, it is inserted according to the ordering of [P].
 */
public fun <H, P : Comparable<P>> Pivot<P, H>.getOrInsert(position: P, lazyValue: () -> H): Pivot<P, H> {
    /**
     * Assumes positions are not equal.
     */
    fun Pivot<P, H>.insert(): Pivot<P, H> {
        val pivot = Pivot(position, lazyValue())
        if (this.position > position) {
            insertBefore(pivot)
        } else {
            insertAfter(pivot)
        }
        return pivot
    }

    if (position.compareTo(this.position) == 0) {
        return this
    }
    var node = this
    if (this.position > position) {
        node = node.backtrace { node.position <= position }
        if (position.compareTo(node.position) == 0) {
            return node
        }
        return node.insert()
    }
    node = node.seek { node.position >= position }
    if (position.compareTo(node.position) == 0) {
        return node
    }
    return node.insert()
}