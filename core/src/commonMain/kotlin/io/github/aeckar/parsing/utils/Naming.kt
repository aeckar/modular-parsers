package io.github.aeckar.parsing.utils

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal fun <ValueT, PropertyT : ReadOnlyProperty<Nothing?, ValueT>> ValueT.readOnlyProperty(): PropertyT {
    return ReadOnlyProperty<Nothing?, ValueT> { _, _ -> this }.unsafeCast()
}

/**
 * Delegating an instance of this class to a property assigns it
 * a [Named] wrapper of the original instance.
 */
public interface Nameable {
    /**
     * Assigns the delegated property a named wrapper containing this instance.
     */
    public operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Nothing?, Named>
}

/**
 * An object with a name.
 */
public interface Named {
    /**
     * The name of this object.
     *
     * If this instance was produced by delegating a [Nameable] object to a property,
     * this string is the same as the name of that property.
     */
    public val name: String
}