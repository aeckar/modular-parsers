package io.github.aeckar.parsing

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Returns a read-only delegate returning this value.
 */
@Suppress("UNCHECKED_CAST")
internal fun <NamedT : Named, ReadOnlyT : ReadOnlyProperty<Any?, NamedT>> NamedT.toNamedProperty(): ReadOnlyT {
    return ReadOnlyProperty<Any?, NamedT> { _, _ -> this } as ReadOnlyT
}

/**
 * Delegating an instance of this class to a property assigns it
 * a [Named] wrapper of the original instance.
 */
public interface Nameable {
    /**
     * Assigns the delegated property a named wrapper containing this instance.
     */
    public operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Named>
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