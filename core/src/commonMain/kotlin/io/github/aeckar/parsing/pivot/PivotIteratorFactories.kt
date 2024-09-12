@file:Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE")

package io.github.aeckar.parsing.pivot

import kotlinx.io.RawSource

/**
 * Returns an iterator pivoting over the elements in the list.
 */
public fun <E, H> List<E>.pivotIterator(init: () -> H): PivotIterator<E, H, Int> {
    val revertible = ListRevertibleIterator(this)
    return object : AbstractPivotIterator<E, H, Int>(
        revertible,
        init
    ), RevertibleIterator<E, Int> by revertible {}
}

/**
 * Returns an iterator pivoting over the characters in this string.
 */
public fun <H> String.pivotIterator(init: () -> H): CharPivotIterator<H, Int> {
    val revertible = StringRevertibleIterator(this)
    return object : AbstractPivotIterator<Char, H, Int>(
        revertible,
        init
    ), CharPivotIterator<H, Int>, CharRevertibleIterator<Int> by revertible {}
}

/**
 * Returns an iterator pivoting over the characters loaded from this source.
 *
 * Making this source buffered provides no performance benefit to the returned iterator.
 * If this is [closed][RawSource.close],
 * any function called from the returned instance throws an [IllegalStateException].
 */
public fun <H> RawSource.pivotIterator(init: () -> H): CharPivotIterator<H, *> {
    val revertible = SourceRevertibleIterator(this)
    return object : AbstractPivotIterator<Char, H, SourcePosition>(
        revertible,
        init
    ), CharPivotIterator<H, SourcePosition>, CharRevertibleIterator<SourcePosition> by revertible {}
}