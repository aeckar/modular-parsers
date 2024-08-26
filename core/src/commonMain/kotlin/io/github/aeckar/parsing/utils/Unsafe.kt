package io.github.aeckar.parsing.utils

import io.github.aeckar.parsing.MalformedParserException

/**
 * Checked `as` casts should be preferred.
 */
@PublishedApi
@Suppress("UNCHECKED_CAST")
internal fun <T> Any?.unsafeCast(): T = this as T

/**
 * A cast that succeeds so long as an unchecked cast as not made to
 * another type or the same type with different type parameters.
 *
 * @throws MalformedParserException the receiver was cast to a different (generic) type
 */
@PublishedApi
internal inline fun <reified T> Any.fragileUnsafeCast(): T {
    return try {
        this as T
    } catch (e: ClassCastException) {
        val t = T::class.qualifiedName
        val r = this::class.qualifiedName
        throw MalformedParserException("Cast to $t fails because object was cast to $r by user", e)
    }
}