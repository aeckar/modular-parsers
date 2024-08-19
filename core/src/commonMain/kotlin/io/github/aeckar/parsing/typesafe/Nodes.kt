package io.github.aeckar.parsing.typesafe

import io.github.aeckar.parsing.Junction
import io.github.aeckar.parsing.Sequence
import io.github.aeckar.parsing.Node
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * A node emitted by a [Junction].
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
 * A node emitted by a [Sequence].
 */
@PublishedApi
internal class SequenceNode(
    symbol: TypeSafeSequence<*>,
    substring: String,
    val branches: List<Node<*>>
) : Node<TypeSafeSequence<*>>(symbol, substring) {
    override val children by lazy { branches.toImmutableList() }
}