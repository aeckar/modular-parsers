package io.github.aeckar.parsing

import kotlin.reflect.KProperty

// To ensure correct polymorphism, the type parameter N must be any object

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
 * A [symbol][Symbol] or [parser][Parser] delegated to a property.
 *
 * The [name] of each instance of this class is the same as the name of the delegated property.
 */
public sealed interface Named {
    public val name: String
}

/**
 * A parser delegated to a property.
 */
public class NamedParser<P : Parser<P>> internal constructor(
    override val name: String,
    internal val base: P
) : Parser<P>(), Named {
    // TODO delegate parser functions to `base` like you did in `NamedSymbol`
}