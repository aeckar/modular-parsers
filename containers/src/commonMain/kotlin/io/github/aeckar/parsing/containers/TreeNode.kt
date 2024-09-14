package io.github.aeckar.parsing.containers

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.PersistentList

/**
 * An element in a tree.
 *
 * Instances may contain child nodes, but do not contain a reference to their parent.
 */
public abstract class TreeNode<Self : TreeNode<Self>> : Iterable<Self> {
    /**
     * The child nodes of this one, or an empty list if none exist.
     *
     * The default implementation of this property is an empty [PersistentList].
     */
    public open val children: List<Self> = persistentListOf()

    /**
     * Contains the specific characters used to create the [treeString] of a node.
     */
    public data class Style(val vertical: Char, val horizontal: Char, val turnstile: Char, val corner: Char) {
        /**
         * Returns a line map containing the given characters.
         * @throws IllegalArgumentException [chars] does not contain exactly 4 characters
         */
        public constructor(chars: String) : this(chars[0], chars[1], chars[2], chars[3]) {
            require(chars.length == 4) { "String '$chars' must have 4 characters'" }
        }
    }

    /**
     * Returns a multi-line string containing the entire tree whose root is this node.
     */
    public fun treeString(lines: Style = UTF_8): String {
        val builder = StringBuilder()
        appendSubtree(builder, lines, layers = BooleanStack())
        builder.deleteAt(builder.lastIndex) // Remove trailing newline
        return builder.toString()
    }

    private fun appendSubtree(
        builder: StringBuilder,
        lines: Style,
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
                prefixWith(lines.turnstile)
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
     * Returns an iterator over the abstract syntax tree whose root is this node
     * in a bottom-up, left-to-right fashion (post-order).
     *
     * The first node returned is the bottom-left-most and the last node returned is this one.
     */
    final override fun iterator(): Iterator<Self> = @Suppress("UNCHECKED_CAST") object : Iterator<Self> {
        private var cursor = this@TreeNode as Self
        private val parentStack = mutableListOf<Self>()
        private val childIndices = IntStack()

        init {
            childIndices += 0   // Prevent underflow in loop condition
            while (cursor.children.isNotEmpty()) {  // Move to bottom-left node
                parentStack.add(cursor)
                cursor = cursor.children.first()
                childIndices += 0
            }
        }

        override fun hasNext() = cursor !== this@TreeNode

        override fun next(): Self {
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
         * Can be passed to [treeString] so that the lines in the returned string are made of UTF-8 characters.
         */
        public val UTF_8: Style = Style("│─├└")

        /**
         * Can be passed to [treeString] so that the lines in the returned string are made of ASCII characters.
         */
        public val ASCII: Style = Style("|-++")
    }
}