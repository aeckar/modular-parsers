package io.github.aeckar.parsing.pivot

import io.github.aeckar.parsing.utils.ListNode

/**
 * Some
 * @param position an object representing the absolute position of this node
 * @param value a value specific to this node
 */
public data class Pivot<H, P : Comparable<P>>(
    public val position: P,
    public val value: H
) : ListNode<Pivot<H, P>>()

/**
 * Returns the node in this linked list whose position has a total ordering equal to [location].
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