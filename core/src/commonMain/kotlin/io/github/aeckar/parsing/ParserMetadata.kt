package io.github.aeckar.parsing

import io.github.aeckar.parsing.utils.InputStream

internal data class ParserMetadata(
    val input: InputStream,
    val skip: Symbol,
    val recursions: MutableList<String> = mutableListOf(),
    val failCache: MutableList<String> = mutableListOf()
)