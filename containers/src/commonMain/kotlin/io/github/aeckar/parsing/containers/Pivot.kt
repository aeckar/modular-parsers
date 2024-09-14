package io.github.aeckar.parsing.containers

/**
 * An object containing a position and a value.
 * @param position the location of this in some larger object
 * @param value a value specific to the location of this
 */
public data class Pivot<P : Comparable<P>, out H>(
    public val position: P,
    public val value: H
) : ListNode<Pivot<P, @UnsafeVariance H>>() {
    /**
     * Returns a pivot with the position and value contained in the pair, respectively.
     */
    public constructor(properties: Pair<P, H>) : this(properties.first, properties.second)
}

/**
 * Returns the pivot whose position has a total ordering equal to [location].
 *
 * If one does not exist, it is inserted according to the ordering of [P].
 * If the receiver is null, the result of [init] is returned.
 */
public fun <H, P : Comparable<P>> Pivot<P, H>?.findOrInsert(location: P, init: () -> Pivot<P, H>): Pivot<P, H> {
    var node = this ?: return init()
    if (location.compareTo(position) == 0) {
        return this
    }
    if (location < position) {
        node = node.seek { location >= node.position }
        if (location.compareTo(node.position) == 0) {
            return node
        }
        return init().also { node.append(it) }
    }
    node = node.backtrace { location <= node.position }
    if (location.compareTo(node.position) == 0) {
        return node
    }
    return init().also { node.prepend(it) }
}