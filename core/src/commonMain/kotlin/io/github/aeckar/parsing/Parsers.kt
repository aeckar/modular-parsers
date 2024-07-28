package io.github.aeckar.parsing

import kotlin.reflect.KProperty

/**
 * Thrown when a [parser definition][parser] is malformed.
 */
public class MalformedParserException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A parser that performs actions according to a defined grammar.
 *
 * Delegating an instance of this class to a property produces a [named parser][NamedParser].
 * This enables the [import][ParserDsl.import] of unqualified symbols from the delegated property.
 */
public sealed class Parser<P : Parser<P>> : Nameable {
    override fun getValue(thisRef: Any?, property: KProperty<*>): NamedParser<P> {
        return NamedParser(property.name, this.unsafeCast())
    }
}

/**
 *
 */
public class StatelessParser : Parser<StatelessParser>() {

}

/**
 *
 */
public class StatefulParser : Parser<StatefulParser>() {

}