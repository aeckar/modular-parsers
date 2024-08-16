package io.github.aeckar.parsing

import io.github.aeckar.parsing.utils.InputStream
import io.github.aeckar.parsing.utils.SourceInputStream
import io.github.aeckar.parsing.utils.StringInputStream
import kotlinx.io.RawSource

internal class ParserMetadata private constructor(
    val input: InputStream,
    val skip: Symbol?,
    val recursions: MutableList<String> = mutableListOf(),
    val failCache: MutableSet<String> = mutableSetOf()
) {
    constructor(input: String, skip: Symbol?) : this(StringInputStream(input), skip)
    constructor(input: RawSource, skip: Symbol?) : this(SourceInputStream(input), skip)
    constructor(input: InputStream) : this(input, null)

    override fun toString() = "{ input = $input, skip = $skip, recursions = $recursions, failCache = $failCache }"
}