package io.github.aeckar.parsing

import io.github.aeckar.parsing.utils.Nameable
import io.github.aeckar.parsing.utils.Named
import io.github.aeckar.parsing.utils.SourceInputStream
import io.github.aeckar.parsing.utils.StringInputStream
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlin.reflect.KProperty

// TODO LATER speed up w/ coroutines

/**
 * Thrown when a [parser definition][parser] is malformed.
 */
public class MalformedParserException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Returns true if a match to the [start][DefinitionDsl.start] of the parser can be made using this string.
 */
public operator fun String.contains(parser: StaticParser): Boolean {
    // ...
}

/**
 * A parser that performs actions according to a formal grammar.
 *
 * Delegating an instance of this class to a property produces a named wrapper of that instance,
 * enabling the [import][DefinitionDsl.import] of named symbols from a parser.
 *
 */
public sealed interface Parser {
    /**
     * Returns the root node of the abstract syntax tree representing the given input.
     */
    public fun toAST(input: Source): Node<*>
}

/**
 * A parser that
 */
public sealed interface LexerParser : Parser {
    /**
     *
     */
    public fun tokenize(input: Source): List<Node<LexerSymbol>>
}

/**
 * A parser whose symbols are resolved at the point of their definition.
 *
 * The definitions of the symbols in this parser will not change after they are assigned.
 */
public sealed class StaticParser : Parser {
    /**
     * An immutable set of all symbols defined in this parser.
     */
    public val symbols: Map<String, NameableSymbol<*>> by lazy { allSymbols.toImmutableMap() }

    internal abstract val allSymbols: Map<String, NameableSymbol<*>>
}

/**
 * A named parser that takes no arguments.
 */
public class NullaryParser(definition: NullaryParserDefinitionDsl) : Parser, Nameable {
    internal val allSymbols =
        HashMap<String, NameableSymbol<*>>(definition.symbols.size + definition.implicitSymbols.size)

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
        skip = if (definition.skipDelegate.isInitialized()) definition.skip else null
        allSymbols += definition.symbols
        listeners = definition.listeners
    }

    /**
     * Invokes the listeners of this parser on the nodes of a
     * completed [abstract syntax tree][toAST] representing the given input.
     *
     * @return the root node of the resulting AST
     */
    public operator fun invoke(input: String): Node<*> = parse(StringInputStream(input))

    /**
     * Invokes the listeners of this parser on the nodes of a
     * completed [abstract syntax tree][toAST] representing the given input.
     *
     * @return the root node of the resulting AST
     */
    public operator fun invoke(input: RawSource): Node<*> = parse(SourceInputStream(input))

    override fun getValue(thisRef: Any?, property: KProperty<*>): NamedNullaryParser {
        return NamedNullaryParser(property.name, this)
    }
}

/**
 * A named parser that takes no arguments.
 */
public class NamedNullaryParser internal constructor(
    override val name: String,
    private val unnamed: NullaryParser
) : StaticParserWithoutArgument(), Parser by unnamed, Named {
    override val allSymbols get() = unnamed.allSymbols
}

/**
 * A parser that takes an argument,
 * and whose symbols are resolved after their initial definition.
 */
public class UnaryParser<A>(definition: DefinitionDsl) : StaticParserWithArgument<A>(), Nameable {
    /**
     *
     */
    public operator fun invoke(argument: A, input: String): AbstractSyntaxTree = parse(argument, StringInputStream(input))

    /**
     *
     */
    public operator fun invoke(argument: A, input: Source): AbstractSyntaxTree = parse(argument, SourceInputStream(input))

    override fun parse(input: Stream): AbstractSyntaxTree {
        TODO("Not yet implemented")
    }

    override val allSymbols: Map<String, NameableSymbol<*>>
        get() = TODO("Not yet implemented")

    override fun tokenize(input: Source): AbstractSyntaxTree {
        TODO("Not yet implemented")
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): Named {
        TODO("Not yet implemented")
    }

}

/**
 * A named parser that takes an argument,
 * and whose symbols are resolved after their initial definition.
 */
public class NamedUnaryParser<A> internal constructor(
    override val name: String,
    private val unnamed: UnaryParser<A>
) : StaticParserWithArgument<A>(), Parser by unnamed, Named {
    override val allSymbols get() = unnamed.allSymbols

    override fun parse(input: Stream) = unnamed.parse(input)
}