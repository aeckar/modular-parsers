package io.github.aeckar.parsing.utils

import io.github.aeckar.parsing.MalformedParserException
import kotlin.properties.ReadOnlyProperty

/**
 * Quiets `UNCHECKED_CAST` warning.
 */
@PublishedApi
@Suppress("UNCHECKED_CAST")
internal fun <T> Any?.unsafeCast(): T = this as T

/**
 * Quiets `LeakingThis` warning in constructors.
 * @return this
 */
internal fun <T> T.self() = this

/**
 * Returns a read-only delegate returning this value.
 */
internal fun <PropertyT, DelegateT : ReadOnlyProperty<Any?, PropertyT>> PropertyT.readOnlyProperty(): DelegateT {
    return ReadOnlyProperty<Any?, PropertyT> { _, _ -> this }.unsafeCast()
}