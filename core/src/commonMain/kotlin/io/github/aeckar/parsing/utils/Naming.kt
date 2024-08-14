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
 *
 * Created when a [Nameable] object is delegated to a property.
 * The [name] of each instance of this class is the same as the name of the property being delegated to.
 */
public interface Named {
    public val name: String
}