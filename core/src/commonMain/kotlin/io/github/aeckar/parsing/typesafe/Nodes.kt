package io.github.aeckar.parsing.typesafe

import io.github.aeckar.parsing.Junction

/**
 * A node emitted by a [Junction].
 */
internal sealed interface JunctionNode {
    val matchOrdinal: Int
}