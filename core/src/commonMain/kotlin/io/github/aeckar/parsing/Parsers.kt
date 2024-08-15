package io.github.aeckar.parsing

import io.github.aeckar.parsing.utils.*
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.io.RawSource
import kotlin.reflect.KProperty

// TODO LATER speed up w/ coroutines

/**
 * Thrown when a [parser definition][parser] is malformed.
 */
public class MalformedParserException(message: String, cause: Throwable? = null) : Exception(message, cause)

private fun Node<*>.walk(listenerStrategy: (Node<*>) -> Unit): Node<*> {
    // Iterative to save memory
    fun walk(root: Node<*>) {
        var cursor = root
        val parents = mutableListOf<Node<*>>()
        val children = IntStack().apply { this += 0 }
        while (cursor.children.isNotEmpty()) {  // Move to bottom-left node
            parents += cursor
            cursor = cursor.children.first()
        }
        listenerStrategy(cursor)
        cursor = parents.removeLast()
        while (cursor !== root) {
            if (cursor.children.isNotEmpty() && children.last() <= cursor.children.lastIndex) {
                children.incrementLast()
                parents += cursor
                cursor = cursor.children[children.last()]
                children += 0
            } else {
                listenerStrategy(cursor)
                cursor = parents.removeLast()
            }
        }
    }

    if (children.isNotEmpty()) {
        walk(this)
    }
    listenerStrategy(this)
    return this
}

/**
 * Returns the start symbol.
 */
private fun resolveSymbols(allSymbols: MutableMap<String, NameableSymbol<*>>, def: ParserDefinition): Symbol {
    def.implicitSymbols.forEach { (name, symbol) ->
        if (symbol == null) {
            throw MalformedParserException("Implicit symbol '$name' is undefined")
        }
        allSymbols[name] = symbol
    }
    allSymbols += def.parserSymbols
    return try {
        def.start
    } catch (e: RuntimeException) {
        throw MalformedParserException("Start symbol for is undefined", e)
    }
}

// ------------------------------ basic parsers ------------------------------

internal val Parser.parserSymbols
    get() = if (this is LexerlessParser) parserSymbols else (this as LexerParser).parserSymbols

internal val Parser.listeners get() = if (this is LexerlessParser) listeners else (this as LexerParser).listeners

private fun NullaryParser.listenerStrategy(node: Node<*>) {
    listeners[toString()]
        ?.unsafeCast<NullaryListener<Symbol>>()
        ?.apply { node.unsafeCast<Node<Symbol>>()() }
}

private fun <ArgumentT> UnaryParser<ArgumentT>.listenerStrategy(argument: ArgumentT, node: Node<*>) {
    listeners[toString()]
        ?.unsafeCast<UnaryListener<Symbol, ArgumentT>>()
        ?.apply { node.unsafeCast<Node<Symbol>>()(argument) }
}

/**
 * Performs actions according to a formal grammar.
 *
 * Delegating an instance of this class to a property produces a named wrapper of that instance,
 * enabling the [import][ParserDefinition.import] of named symbols from a parser.
 */
public sealed interface Parser {
    /**
     * An immutable map of all symbols defined in this parser.
     */
    public val symbols: Map<String, NameableSymbol<*>>

    /**
     * Returns the root node of the abstract syntax tree representing the input, if one exists.
     */
    public fun parse(input: String): Node<*>?

    /**
     * Returns the root node of the abstract syntax tree representing the input, if one exists.
     */
    public fun parse(input: RawSource): Node<*>?
}

/**
 * A parser given a name by delegating a [Nameable] parser to a property.
 */
public sealed interface NamedParser : Parser

/**
 * A parser that does not take an argument.
 */
public sealed interface NullaryParser : Parser {
    /**
     * Invokes the listeners of this parser on the nodes of a
     * completed [abstract syntax tree][parse] representing the given input.
     *
     * @return the root node of the resulting AST
     */
    public operator fun invoke(input: String): Node<*>?

    /**
     * See [NullaryParser.invoke] for details.
     */
    public operator fun invoke(input: RawSource): Node<*>?
}

/**
 * A parser that takes one argument.
 */
public sealed interface UnaryParser<ArgumentT> : Parser {
    /**
     * Invokes the listeners of this parser on the nodes of a
     * completed [abstract syntax tree][parse] representing the given input.
     *
     * The argument passed to this function is also passed to each listener.
     * @return the root node of the resulting AST
     */
    public operator fun invoke(argument: ArgumentT, input: String): Node<*>?

    /**
     * See [UnaryParser.invoke] for details.
     */
    public operator fun invoke(argument: ArgumentT, input: RawSource): Node<*>?
}

// ------------------------------ lexerless parsers ------------------------------

/**
 * A parser that creates an abstract syntax tree directly from its input.
 */
public sealed class LexerlessParser(def: LexerlessParserDefinition) : Parser {
    internal val parserSymbols = HashMap<String, NameableSymbol<*>>(def.parserSymbols.size + def.implicitSymbols.size)
    internal val listeners = def.listeners

    private val start = resolveSymbols(parserSymbols, def)
    private val skip = if (def.skipDelegate.isInitialized()) def.skip else null

    final override val symbols: Map<String, NameableSymbol<*>> by lazy { parserSymbols.toImmutableMap() }

    final override fun parse(input: String): Node<*>? = start.match(ParserMetadata(input, skip))
    final override fun parse(input: RawSource): Node<*>? = start.match(ParserMetadata(input, skip))
}

/**
 * A named parser that takes no arguments.
 */
public class NullaryLexerlessParser internal constructor(
    def: NullaryLexerlessParserDefinition
) : LexerlessParser(def), Nameable, NullaryParser {
    override fun invoke(input: String): Node<*>? = parse(input)?.walk(::listenerStrategy)
    override fun invoke(input: RawSource): Node<*>? = parse(input)?.walk(::listenerStrategy)

    override fun getValue(thisRef: Any?, property: KProperty<*>): NamedNullaryLexerlessParser {
        return NamedNullaryLexerlessParser(property.name, this)
    }
}

/**
 * A named [LexerlessParser] that does not take an argument.
 */
public class NamedNullaryLexerlessParser internal constructor(
    override val name: String,
    private val unnamed: NullaryLexerlessParser
) : Named, NullaryParser by unnamed

/**
 * A parser that takes an argument,
 * and whose symbols are resolved after their initial definition.
 */
public class UnaryLexerlessParser<ArgumentT> internal constructor(
    def: UnaryLexerlessParserDefinition<ArgumentT>
) : LexerlessParser(def), Nameable, UnaryParser<ArgumentT> {
    private val initializer = def.initializer

    override fun invoke(argument: ArgumentT, input: String): Node<*>? {
        initializer?.let { it(argument) }
        return parse(input)?.walk { listenerStrategy(argument, it) }
    }

    override fun invoke(argument: ArgumentT, input: RawSource): Node<*>? {
        initializer?.let { it(argument) }
        return parse(input)?.walk { listenerStrategy(argument, it) }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): NamedUnaryLexerlessParser<ArgumentT> {
        return NamedUnaryLexerlessParser(property.name, this)
    }
}

/**
 * A named [LexerlessParser] that takes one argument.
 */
public class NamedUnaryLexerlessParser<ArgumentT> internal constructor(
    override val name: String,
    private val unnamed: UnaryLexerlessParser<ArgumentT>
) : Named, UnaryParser<ArgumentT> by unnamed

// ------------------------------ lexer-parsers ------------------------------

/**
 * Transforms an input into a list of [Token]s.
 */
public sealed interface Lexer {
    /**
     * Returns a list of tokens representing the input.
     */
    public fun tokenize(input: String): List<Token>

    /**
     * Returns a list of tokens representing the input.
     */
    public fun tokenize(input: RawSource): List<Token>
}

/**
 * A parser that tokenizes its input before parsing.
 */
public sealed class LexerParser(def: LexerParserDefinition) : Lexer, Parser {
    internal val parserSymbols = HashMap<String, NameableSymbol<*>>(def.parserSymbols.size + def.implicitSymbols.size)
    internal val listeners = def.listeners

    private val start = resolveSymbols(parserSymbols, def)
    private val skip = def.skip.toImmutableList()
    private val lexerSymbols = def.lexerSymbols

    final override val symbols: Map<String, NameableSymbol<*>> by lazy {
        HashMap<String, NameableSymbol<*>>(parserSymbols.size + lexerSymbols.size).apply {
            putAll(parserSymbols)
            putAll(lexerSymbols.associate { it.name to it.unnamed })
        }.toImmutableMap()
    }

    final override fun parse(input: String): Node<*>? = start.match(ParserMetadata(input, skip))
    final override fun parse(input: RawSource): Node<*>? = start.match(ParserMetadata(input, skip))

    final override fun tokenize(input: String): List<Token> {
        lexer
    }

    final override fun tokenize(input: RawSource): List<Token> {
        TODO("Not yet implemented")
    }

    private fun tokenize(input: InputStream): List<Token> {

    }
}

/**
 * A [LexerParser] that does not take an argument.
 */
public class NullaryLexerParser internal constructor(
    def: NullaryLexerParserDefinition
) : Nameable, NullaryParser, Lexer {

}

/**
 * A named [LexerParser] that does not take an argument.
 */
public class NamedNullaryLexerParser internal constructor(
    override val name: String,
    private val unnamed: NullaryLexerParser
) : Named, NullaryParser by unnamed, Lexer by unnamed

/**
 * A [LexerParser] that takes one argument.
 */
public class UnaryLexerParser<ArgumentT> internal constructor(
    def: UnaryLexerParserDefinition<ArgumentT>
) : Nameable, UnaryParser<ArgumentT>, Lexer {

}

/**
 * A named [LexerParser] that takes one argument.
 */
public class NamedUnaryLexerParser<ArgumentT> internal constructor(
    override val name: String,
    private val unnamed: UnaryLexerParser<ArgumentT>
) : Named, UnaryParser<ArgumentT> by unnamed, Lexer by unnamed