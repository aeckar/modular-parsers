package io.github.aeckar.parsing.typesafe

import io.github.aeckar.parsing.Node
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * A node emitted by a junction.
 */
@PublishedApi
internal class JunctionNode(
    symbol: TypeSafeJunction<*>,
    substring: String,
    match: Node<*>,
    val matchOrdinal: Int
) : Node<TypeSafeJunction<*>>(symbol, substring) {
    override val children = persistentListOf(match)
}

/**
 * A node emitted by a sequence.
 */
@PublishedApi
internal class SequenceNode(
    symbol: TypeSafeSequence<*>,
    substring: String,
    val branches: List<Node<*>>
) : Node<TypeSafeSequence<*>>(symbol, substring) {
    override val children by lazy { branches.toImmutableList() }
}