package io.github.aeckar.parsing.utils

import kotlin.reflect.KProperty

/**
 * A delegate ensuring that a property is only assigned a value once.
 */
public class OnceAssignable<FieldT : Any, ExceptionT : Throwable>(throws: (String) -> ExceptionT) {
    private val raise = throws
    private lateinit var name: String
    private var field: FieldT? = null

    /**
     * Returns true if the delegated property has been given a value.
     */
    public fun isInitialized(): Boolean = field != null

    /**
     * Assigns the name of the property to this delegate.
     */
    public operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): OnceAssignable<FieldT, ExceptionT> {
        name = property.name
        return this
    }

    /**
     * Returns the backing field of this property.
     * @throws ExceptionT the property is accessed before it is given a value
     */
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): FieldT {
        field?.let { return it }
        throw raise("Property '$name' is accessed before it is given a value")
    }

    /**
     * Assigns an initial value to the backing field of this property.
     * @throws ExceptionT the property is assigned a value more than once
     */
    public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: FieldT) {
        field?.let { throw raise("Property '$name' is assigned a value more than once") }
        field = value
    }
}