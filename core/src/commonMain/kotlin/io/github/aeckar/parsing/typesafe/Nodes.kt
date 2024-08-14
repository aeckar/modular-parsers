package io.github.aeckar.parsing.typesafe

import io.github.aeckar.parsing.Junction

/**
 * A node emitted by a [junction symbol][Junction].
 */
internal sealed interface JunctionNode {
    val matchOrdinal: Int
}