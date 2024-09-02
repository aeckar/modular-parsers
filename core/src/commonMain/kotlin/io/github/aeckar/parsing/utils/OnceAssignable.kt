package io.github.aeckar.parsing.utils

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A delegate ensuring that a property is only assigned a value once.
 * @param raise thrown when the above contract is violated
 */
public class OnceAssignable<FieldT : Any, ExceptionT : Throwable>(
    private val raise: (String) -> ExceptionT
) : ReadWriteProperty<Any?, FieldT> {
    /**
     * The backing field of this delegate.
     */
    public var field: FieldT? = null
        private set

    private lateinit var name: String

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
    override fun getValue(thisRef: Any?, property: KProperty<*>): FieldT {
        field?.let { return it }
        throw raise("Property '$name' is accessed before it is given a value")
    }

    /**
     * Assigns an initial value to the backing field of this property.
     * @throws ExceptionT the property is assigned a value more than once
     */
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: FieldT) {
        field?.let { throw raise("Property '$name' is assigned a value more than once") }
        field = value
    }
}