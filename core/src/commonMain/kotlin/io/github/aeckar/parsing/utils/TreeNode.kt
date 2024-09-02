package io.github.aeckar.parsing.utils

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.PersistentList

/**
 * An element in a tree.
 *
 * Instances may contain child nodes, but do not contain a reference to their parent.
 */
public abstract class TreeNode<ChildT : TreeNode<ChildT>> : Iterable<ChildT> {
    /**
     * The child nodes of this one, or an empty list if none exist.
     *
     * The default implementation of this property is an empty [PersistentList].
     */
    public open val children: List<ChildT> = persistentListOf()

    /**
     * Returns a multi-line string containing the entire tree whose root is this node.
     */
    public fun treeString(lines: LineMap = UTF_8): String {
        val builder = StringBuilder()
        appendSubtree(builder, lines, layers = BooleanStack())
        return builder.toString()
    }

    /**
     * TODO
     */
    private fun appendSubtree(
        builder: StringBuilder,
        lines: LineMap,
        layers: BooleanStack
    ): Unit = with(builder) {
        fun prefixWith(corner: Char) {
            layers.forEach { append(if (it) "${lines.vertical}   " else "    ") }
            append(corner)
            append(lines.horizontal)
            append(lines.horizontal)
            append(' ')
        }

        append(this@TreeNode)
        append('\n')
        if (children.isNotEmpty()) {
            children.asSequence().take(children.size.coerceAtLeast(1) - 1).forEach {
                prefixWith(lines.fork)
                layers += true
                it.appendSubtree(builder, lines, layers)
                layers.removeLast()
            }
            prefixWith(lines.corner)
            layers += false
            children.last().appendSubtree(builder, lines, layers)
            layers.removeLast()
        }
    }

    /**
     * TODO
     */
    public data class LineMap(val vertical: Char, val horizontal: Char, val fork: Char, val corner: Char) {
        /**
         * TODO
         */
        public constructor(chars: String) : this(chars[0], chars[1], chars[2], chars[3]) {
            require(chars.length == 4) { "String '$chars' must have 4 characters'" }
        }
    }

    /**
     * Returns an iterator over the abstract syntax tree whose root is this node
     * in a bottom-up, left-to-right fashion (post-order).
     *
     * The first node returned is the bottom-left-most and the last node returned is this one.
     */
    final override fun iterator(): Iterator<ChildT> = object : Iterator<ChildT> {
        private var cursor: ChildT = this@TreeNode.unsafeCast()
        private val parentStack = mutableListOf<ChildT>()
        private val childIndices = IntStack()
        private var firstIteration = true

        init {
            childIndices += 0   // Prevent underflow in loop condition
            while (cursor.children.isNotEmpty()) {  // Move to bottom-left node
                parentStack.add(cursor)
                cursor = cursor.children.first()
                childIndices += 0
            }
        }

        override fun hasNext() = cursor !== this@TreeNode

        override fun next(): ChildT {
            if (firstIteration) {
                firstIteration = false
                return cursor
            }
            cursor = parentStack.removeLast()
            childIndices.removeLast()
            while (childIndices.last() <= cursor.children.lastIndex) {
                parentStack.add(cursor)
                cursor = cursor.children[childIndices.last()]
                childIndices.mapLast(Int::inc)
                childIndices += 0
            }
            return cursor
        }
    }

    public companion object {
        /**
         * Can be passed to [appendSubtree] so that the lines in the returned string are made of UTF-8 characters.
         */
        public val UTF_8: LineMap = LineMap("│─├└")

        /**
         * Can be passed to [appendSubtree] so that the lines in the returned string are made of ASCII characters.
         */
        public val ASCII: LineMap = LineMap("|-++")
    }
}