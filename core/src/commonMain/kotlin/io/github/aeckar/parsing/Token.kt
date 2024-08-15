package io.github.aeckar.parsing

/**
 * A [substring] matched in some larger input.
 *
 * @param name the name of the symbol that produced this token
 * @param substring a substring matched in some larger input
 */
public data class Token(val name: String, val substring: String)