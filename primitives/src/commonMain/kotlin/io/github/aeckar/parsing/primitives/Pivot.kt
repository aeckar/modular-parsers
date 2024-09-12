package io.github.aeckar.parsing.primitives

/**
 * An object containing a position and a value.
 * @param position the location of this in some larger object
 * @param value a value specific to the location of this
 */
public data class Pivot<H, P : Comparable<P>>(
    public val position: P,
    public val value: H
) : ListNode<Pivot<H, P>>()

/**
 * Returns the pivot whose position has a total ordering equal to [location].
 *
 * If one does not exist, it is inserted according to the ordering of [P].
 * If the receiver is null, the result of [init] is returned.
 */
public fun <H, P : Comparable<P>> Pivot<H, P>?.findOrInsert(location: P, init: () -> Pivot<H, P>): Pivot<H, P> {
    var node = this ?: return init()
    if (location.compareTo(position) == 0) {
        return this
    }
    if (location < position) {
        while (node.isNotHead() && location < node.position) {
            node = node.last()
        }
        if (location.compareTo(node.position) == 0) {
            return node
        }
        return init().also { node.append(it) }
    }
    while (node.isNotTail() && location > node.position) {
        node = node.next()
    }
    if (location.compareTo(node.position) == 0) {
        return node
    }
    return init().also { node.prepend(it) }
}