package io.github.aeckar.parsing

import kotlin.reflect.KProperty

/**
 * Delegating an instance of this class to a property assigns it
 * a [named wrapper][Named] of the original instance.
 */
public sealed interface Nameable {
    /**
     * Assigns the delegated property a named wrapper containing this instance.
     */
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): Named
}

/**
 * A [symbol][Symbol] or parser delegated to a property.
 *
 * The [name] of each instance of this class is the same as the name of the delegated property.
 */
public sealed interface Named {
    public val name: String
}