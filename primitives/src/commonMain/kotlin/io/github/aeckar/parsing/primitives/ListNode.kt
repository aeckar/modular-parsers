@file:Suppress("DuplicatedCode")    // False positive
package io.github.aeckar.parsing.primitives

/**
 * An element in a linked list.
 */
public abstract class ListNode<Self : ListNode<Self>> {
    private var next: Self? = null
    private var last: Self? = null

    /**
     * Returns the next node in the linked list.
     */
    public fun next(): Self {
        return next ?: throw IllegalArgumentException("Node is tail")
    }

    /**
     * Returns the previous node in the linked list.
     */
    public fun last(): Self {
        return last ?: throw IllegalArgumentException("Node is head")
    }

    /**
     * Returns true if this node has no last node.
     */
    public fun isHead(): Boolean = last == null

    /**
     * Returns true if this node has no next node.
     */
    public fun isTail(): Boolean = next == null

    /**
     * Returns true if this node has a last node.
     */
    public fun isNotHead(): Boolean = last != null

    /**
     * Returns true if this node has a next node.
     */
    public fun isNotTail(): Boolean = next != null

    /**
     * Inserts the given node directly after this one.
     * @throws IllegalArgumentException [node] is this same instance
     */
    public fun append(node: Self) {
        require(this !== node) { "Cannot append node to itself" }
        next?.apply { last = node }
        node.next = next
        @Suppress("UNCHECKED_CAST")
        node.last = this as Self
        next = node
    }

    /**
     * Inserts the given node directly before this one.
     * @throws IllegalArgumentException [node] is this same instance
     */
    public fun prepend(node: Self) {
        require(this !== node) { "Cannot append node to itself" }
        last?.apply { next = node }
        node.last = last
        @Suppress("UNCHECKED_CAST")
        node.next = this as Self
        last = node
    }
}