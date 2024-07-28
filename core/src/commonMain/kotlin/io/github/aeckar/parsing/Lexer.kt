package io.github.aeckar.parsing

import kotlinx.io.Source

internal data class Lexer(
    val input: Source,
    val skip: Symbol,
    val recursions: MutableList<String> = mutableListOf(),
    val failCache: MutableList<String> = mutableListOf()
)