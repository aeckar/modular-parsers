package io.github.aeckar.parsing.utils

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A delegate ensuring that a property is only assigned a value once.
 * @param raise thrown when the above contract is violated
 */
internal class OnceAssignable<T : Any, X : Throwable>(
    private val raise: (String) -> X
) : ReadWriteProperty<Any?, T> {
    var field: T? = null
        private set

    private lateinit var name: String

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): OnceAssignable<T, X> {
        name = property.name
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        field?.let { return it }
        throw raise("Property '$name' is accessed before it is given a value")
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        field?.let { throw raise("Property '$name' is assigned a value more than once") }
        field = value
    }
}