package io.github.aeckar.parsing.utils

import kotlin.properties.ReadOnlyProperty

/**
 * Quiets `UNCHECKED_CAST` warning.
 */
@PublishedApi
@Suppress("UNCHECKED_CAST")
internal fun <T> Any?.unsafeCast(): T = this as T

/**
 * Returns a read-only delegate returning this value.
 */
internal fun <PropertyT, DelegateT : ReadOnlyProperty<Any?, PropertyT>> PropertyT.readOnlyProperty(): DelegateT {
    return ReadOnlyProperty<Any?, PropertyT> { _, _ -> this }.unsafeCast()
}