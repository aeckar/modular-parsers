package io.github.aeckar.parsing

import kotlin.reflect.KProperty

/**
 * Thrown when a [parser definition][parser] is malformed.
 */
public class MalformedParserException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A parser that performs actions according to a defined grammar.
 *
 * Delegating an instance of this class to a property produces a named wrapper of that instance,
 * enabling the [import][ParserDefinitionDsl.import] of named symbols from a parser.
 */
public sealed class StaticParser {
    internal abstract val allSymbols: HashMap<String, NameableSymbol<*>>
}

//regex()() in "ewfefw"
//regex("ewfwe") in "ewaewfw"


// () : {something with in}
// ()() : {something with in}



/**
 * A named parser that takes no arguments.
 */
public class NullaryParser(definition: ParserDefinitionDsl) : StaticParser(), Nameable {
    override val allSymbols: HashMap<String, NameableSymbol<*>> =
        HashMap(definition.symbols.size + definition.implicitSymbols.size)

    private val start: Symbol
    private val skip: Symbol?
    private val listeners: MutableMap<String, Listener<*>>

    init {
        definition.implicitSymbols.forEach { (name, symbol) ->
            if (symbol == null) {
                throw MalformedParserException("Implicit symbol '$name' is undefined")
            }
            allSymbols[name] = symbol
        }
        try {
            start = definition.start
        } catch (e: RuntimeException) {
            throw MalformedParserException("Start symbol for is undefined", e)
        }
        skip = definition.skipOrNull()
        allSymbols += definition.symbols
        listeners = definition.listeners
    }

    public operator fun invoke(): ParserOperator {

    }

    public operator fun contains(s: String) {

    }
    override fun getValue(thisRef: Any?, property: KProperty<*>): NamedNullaryParser {
        return NamedNullaryParser(property.name, this)
    }
}

public class NamedNullaryParser internal constructor(
    override val name: String,
    private val unnamed: NullaryParser
) : Named {

}

public class UnaryParser(definition: ParserDefinitionDsl) :

/**
 * A named parser that takes an argument.
 */
public class NamedParserOperator internal constructor(
    name: String,
    unnamed: NullaryParser
) : AbstractNamedParser(name, unnamed) {

}

public class Na