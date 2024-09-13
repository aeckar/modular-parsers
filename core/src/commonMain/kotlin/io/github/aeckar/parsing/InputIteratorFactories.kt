package io.github.aeckar.parsing

import io.github.aeckar.parsing.containers.PivotIterator
import io.github.aeckar.parsing.containers.pivotIterator
import kotlinx.io.RawSource

internal typealias InputIterator<E, P> = PivotIterator<E, P, InputPosition>

/**
 * Returns an iterator pivoting over the elements in the list.
 */
internal fun <E> List<E>.inputIterator(): InputIterator<E, Int> = pivotIterator(::InputPosition)

/**
 * Returns an iterator pivoting over the characters in this string.
 */
internal fun String.inputIterator(): InputIterator<Char, Int> = pivotIterator(::InputPosition)

/**
 * Returns an iterator pivoting over the characters loaded from this source.
 *
 * Making this source buffered provides no performance benefit to the returned iterator.
 * If this is [closed][RawSource.close],
 * any function called from the returned instance throws an [IllegalStateException].
 */
internal fun RawSource.inputIterator(): InputIterator<Char, *> = pivotIterator(::InputPosition)