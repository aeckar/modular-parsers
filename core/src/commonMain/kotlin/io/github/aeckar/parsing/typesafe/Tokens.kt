package io.github.aeckar.parsing.typesafe

import io.github.aeckar.parsing.Junction

/**
 * A token emitted by a [junction symbol][Junction].
 */
internal sealed interface JunctionToken {
    val matchOrdinal: Int
}