@file:JvmName("Nodes")
package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.JunctionNode
import io.github.aeckar.parsing.typesafe.TypeSafeJunction
import io.github.aeckar.parsing.utils.IntStack
import io.github.aeckar.parsing.utils.fragileUnsafeCast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.jvm.JvmName

// Functions with Token<...> receiver must be extensions to ensure proper nesting of token contexts in listeners

/**
 * A matched substring in a given input produced according to the matching logic of a symbol.
 *
 * When nodes are combined into a hierarchy, they form an [abstract syntax tree][Parser.parse].
 *
 * Because extension functions of this class are specific to the parameter [MatchT],
 * performing a cast to an instance with another type parameter may break the API.
 */
public open class Node<MatchT : Symbol> internal constructor(
    internal var source: MatchT,

    /**
     * The substring matched by the symbol that produced this node.
     */
    public val substring: String
) : Iterable<Node<*>> {
    /**
     * The children of this node, each as the root of an abstract syntax tree.
     */
    public open val children: List<Node<*>> = persistentListOf()    // Empty for leaf nodes

    /**
     * The name assigned to this symbol, or null if one does not exist.
     */
    public fun isNamed(): Boolean = source is NameableSymbol<*>

    /**
     * Returns an iterator over the abstract syntax tree whose root is this node in a bottom-up, left-to-right fashion.
     *
     * The first node returned is the bottom-left-most and the last node returned is this one.
     */
    final override fun iterator(): Iterator<Node<*>> = object : Iterator<Node<*>> {
        private var cursor: Node<*> = this@Node
        private val parentStack = mutableListOf<Node<*>>()
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

        override fun hasNext() = cursor !== this@Node

        override fun next(): Node<*> {
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

    /**
     * Returns the name assigned to the symbol that produced this node if it exists,
     * else the symbol's EBNF representation.
     */
    final override fun toString(): String = source.rawName
}

// ------------------------------ option nodes ------------------------------

/**
 * A token emitted by an [Option].
 */
@PublishedApi
internal class OptionNode<SubMatchT : Symbol> internal constructor(
    symbol: Option<SubMatchT>,
    substring: String,
    @PublishedApi internal val match: Node<SubMatchT>?
) : Node<Option<SubMatchT>>(symbol, substring)

/**
 * Returns true if a match to a token was made.
 */
public fun Node<Option<*>>.matchSucceeded(): Boolean = fragileUnsafeCast<OptionNode<*>>().match != null

/**
 * Returns true if a match to a token was not made.
 */
public fun Node<Option<*>>.matchFailed(): Boolean = fragileUnsafeCast<OptionNode<*>>().match == null

/**
 * Performs the [action] using the matched token, [if present][matchSucceeded].
 * Otherwise, does nothing.
 */
public inline fun <SubMatchT : Symbol> Node<Option<SubMatchT>>.onSuccess(action: Node<SubMatchT>.() -> Unit) {
    fragileUnsafeCast<OptionNode<SubMatchT>>().match?.apply(action)
}

// ------------------------------ repetition nodes ------------------------------

/**
 * A token emitted by a [Repetition].
 */
internal class RepetitionNode<SubMatchT : Symbol> internal constructor(
    symbol: Repetition<SubMatchT>,
    substring: String,
    internal val branches: List<Node<SubMatchT>>
) : Node<Repetition<SubMatchT>>(symbol, substring) {
    override val children: List<Node<SubMatchT>> by lazy { branches.toImmutableList() }
}

// ------------------------------ junction nodes ------------------------------

/**
 * The index of the option matched by the symbol emitting this token.
 */
public val Node<out TypeUnsafeJunction<*>>.matchOrdinal: Int get() = fragileUnsafeCast<JunctionNode>().matchOrdinal

/**
 * The index of the option matched by the symbol emitting this token.
 */
@get:JvmName("typeSafeMatchOrdinal")
public val Node<TypeSafeJunction<*>>.matchOrdinal: Int get() = fragileUnsafeCast<JunctionNode>().matchOrdinal