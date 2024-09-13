package io.github.aeckar.parsing.containers

import kotlinx.io.RawSource

/**
 * Returns a revertible iterator over the elements in the list.
 */
public fun <E> List<E>.revertibleIterator(): RevertibleIterator<E, Int> = ListRevertibleIterator(this)

/**
 * Returns a revertible iterator over the characters in this string.
 */
public fun String.revertibleIterator(): CharRevertibleIterator<Int> = StringRevertibleIterator(this)

/**
 * Returns a revertible iterator over the characters loaded from this source.
 *
 * Making this source buffered provides no performance benefit to the returned iterator.
 * If this is [closed][RawSource.close],
 * any function called from the returned instance throws an [IllegalStateException].
 */
public fun RawSource.revertibleIterator(): CharRevertibleIterator<*> = SourceRevertibleIterator(this)