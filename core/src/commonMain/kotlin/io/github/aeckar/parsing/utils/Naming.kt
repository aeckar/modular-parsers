package io.github.aeckar.parsing.utils

import kotlin.reflect.KProperty

/**
 * Delegating an instance of this class to a property assigns it
 * a [Named] wrapper of the original instance.
 */
public interface Nameable {
    /**
     * Assigns the delegated property a named wrapper containing this instance.
     */
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): Named
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