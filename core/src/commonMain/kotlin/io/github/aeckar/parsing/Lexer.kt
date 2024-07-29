package io.github.aeckar.parsing

internal data class Lexer(
    val input: String,
    val skip: Symbol,
    val recursions: MutableList<String> = mutableListOf(),
    val failCache: MutableList<String> = mutableListOf()
)