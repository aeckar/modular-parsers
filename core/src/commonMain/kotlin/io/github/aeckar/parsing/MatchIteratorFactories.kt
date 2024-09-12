package io.github.aeckar.parsing

import io.github.aeckar.parsing.primitives.PivotIterator
import io.github.aeckar.parsing.primitives.pivotIterator
import kotlinx.io.RawSource

internal typealias MatchIterator<E, P> = PivotIterator<E, MatchAttempt, P>

/**
 * Returns an iterator pivoting over the elements in the list.
 */
internal fun <E> List<E>.matchIterator(): MatchIterator<E, Int> = pivotIterator(::MatchAttempt)

/**
 * Returns an iterator pivoting over the characters in this string.
 */
internal fun String.matchIterator(): MatchIterator<Char, Int> = pivotIterator(::MatchAttempt)

/**
 * Returns an iterator pivoting over the characters loaded from this source.
 *
 * Making this source buffered provides no performance benefit to the returned iterator.
 * If this is [closed][RawSource.close],
 * any function called from the returned instance throws an [IllegalStateException].
 */
internal fun RawSource.matchIterator(): MatchIterator<Char, *> = pivotIterator(::MatchAttempt)