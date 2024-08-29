@file:Suppress("LeakingThis")   // Parser ID hashcode

package io.github.aeckar.parsing

import io.github.aeckar.parsing.utils.*
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.io.RawSource
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

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
private fun ParserDefinition.resolveSymbols(): Map<String, NameableSymbol<*>> {
    val allSymbols = HashMap<String, NameableSymbol<*>>(parserSymbols.size + implicitSymbols.size)
    implicitSymbols.forEach { (name, symbol) ->
        if (symbol == null) {
            throw MalformedParserException("Implicit symbol '$name' is undefined")
        }
        allSymbols[name] = symbol
    }
    allSymbols += parserSymbols
    return allSymbols
}

/**
 * @throws MalformedParserException always
 */
private fun Parser.raiseUndefinedStart(): Nothing {
    val name = if (this is Named) name  else ""
    throw MalformedParserException("Start symbol for parser '$name' is undefined")
}

// ------------------------------ basic parsers ------------------------------

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

// The following extensions used by ParserDefinition

internal val Parser.parserSymbols: Map<String, NameableSymbol<*>> get() = when (this) {
    is NameableLexerlessParser -> parserSymbols
    is NameableLexerParser -> parserSymbols
    is NamedNullaryLexerlessParser -> unnamed.parserSymbols
    is NamedUnaryLexerlessParser<*> -> unnamed.parserSymbols
    is NamedNullaryLexerParser -> unnamed.parserSymbols
    is NamedUnaryLexerParser<*> -> unnamed.parserSymbols
}

internal val Parser.listeners get() = when (this) {
    is NameableLexerlessParser -> listeners
    is NameableLexerParser -> listeners
    is NamedNullaryLexerlessParser -> unnamed.listeners
    is NamedUnaryLexerlessParser<*> -> unnamed.listeners
    is NamedNullaryLexerParser -> unnamed.listeners
    is NamedUnaryLexerParser<*> -> unnamed.listeners
}

/**
 * A named parser.
 */
public sealed interface NamedParser : Named, Parser

/**
 * A parser that does not take an argument.
 */
public sealed interface NullaryParser : Parser

/**
 * A named nullary parser.
 */
public sealed interface NamedNullaryParser : NamedParser, NullaryParser

/**
 * Invokes the listeners of this parser on the nodes of a
 * completed [abstract syntax tree][Parser.parse] representing the given input.
 *
 * @return the root node of the resulting AST
 */
public operator fun NullaryParser.invoke(input: String): Node<*>? = parse(input)?.walk(::listenerStrategy)

/**
 * See [NullaryParser.invoke] for details.
 */
public operator fun NullaryParser.invoke(input: RawSource): Node<*>? = parse(input)?.walk(::listenerStrategy)

private fun NullaryParser.listenerStrategy(node: Node<*>) {
    listeners[toString()]
        ?.unsafeCast<NullarySymbolListener<Symbol>>()
        ?.apply { node.unsafeCast<Node<Symbol>>()() }
}

/**
 * A parser that takes one argument.
 */
public sealed interface UnaryParser<ArgumentT> : Parser

/**
 * A named unary parser.
 */
public sealed interface NamedUnaryParser<ArgumentT> : NamedParser, UnaryParser<ArgumentT>

/**
 * Invokes the listeners of this parser on the nodes of a
 * completed [abstract syntax tree][Parser.parse] representing the given input.
 *
 * The argument passed to this function is also passed to each listener.
 * @return the root node of the resulting AST
 */
public operator fun <ArgumentT> UnaryParser<ArgumentT>.invoke(argument: ArgumentT, input: String): Node<*>? {
    initializer?.let { it(argument) }
    return parse(input)?.walk { listenerStrategy(argument, it) }
}

/**
 * See [UnaryParser.invoke] for details.
 */
public operator fun <ArgumentT> UnaryParser<ArgumentT>.invoke(argument: ArgumentT, input: RawSource): Node<*>? {
    initializer?.let { it(argument) }
    return parse(input)?.walk { listenerStrategy(argument, it) }
}

private val <ArgumentT> UnaryParser<ArgumentT>.initializer get() =
    if (this is UnaryLexerlessParser<ArgumentT>) initializer else (this as UnaryLexerParser<ArgumentT>).initializer

private fun <ArgumentT> UnaryParser<ArgumentT>.listenerStrategy(argument: ArgumentT, node: Node<*>) {
    listeners[toString()]
        ?.unsafeCast<UnarySymbolListener<Symbol, ArgumentT>>()
        ?.apply { node.unsafeCast<Node<Symbol>>()(argument) }
}

// ------------------------------ lexerless parsers ------------------------------

/**
 * A parser that creates an abstract syntax tree directly from its input.
 */
public sealed class NameableLexerlessParser(def: LexerlessParserDefinition) : Parser {
    internal val parserSymbols = def.resolveSymbols()
    internal val listeners = def.listeners
    internal val id = hashCode()

    private val start = def.startDelegate.field
    private val skip = def.skipDelegate.field

    final override val symbols: Map<String, NameableSymbol<*>> by lazy { parserSymbols.toImmutableMap() }

    init {
        def.inversionSymbols.forEach { it.origin = this }
        debug { "Defined with parser symbols $parserSymbols" }
    }

    final override fun parse(input: String): Node<*>? {
        return (start ?: raiseUndefinedStart()).match(ParserMetadata(input, skip))
    }

    final override fun parse(input: RawSource): Node<*>? {
        return (start ?: raiseUndefinedStart()).match(ParserMetadata(input, skip))
    }

    final override fun toString(): String = "Parser ${id.toString(radix = 16)}"
}

/**
 * A named parser that takes no arguments.
 */
public class NullaryLexerlessParser internal constructor(
    def: NullaryLexerlessParserDefinition
) : NameableLexerlessParser(def), Nameable, NullaryParser {
    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>
    ): ReadOnlyProperty<Nothing?, NamedNullaryLexerlessParser> {
        return NamedNullaryLexerlessParser(property.name, this).readOnlyProperty()
    }
}

/**
 * A named [NameableLexerlessParser] that does not take an argument.
 */
public class NamedNullaryLexerlessParser internal constructor(
    override val name: String,
    internal val unnamed: NullaryLexerlessParser
) : NamedNullaryParser, NullaryParser by unnamed {
    init {
        debug { "Named at ${unnamed.id.toString(radix = 16)}" }
    }

    override fun toString(): String = name
}

/**
 * A parser that takes an argument,
 * and whose symbols are resolved after their initial definition.
 */
public class UnaryLexerlessParser<ArgumentT> internal constructor(
    def: UnaryLexerlessParserDefinition<ArgumentT>
) : NameableLexerlessParser(def), Nameable, UnaryParser<ArgumentT> {
    internal val initializer = def.initializer

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>
    ): ReadOnlyProperty<Nothing?, NamedUnaryLexerlessParser<ArgumentT>> {
        return NamedUnaryLexerlessParser(property.name, this).readOnlyProperty()
    }
}

/**
 * A named [NameableLexerlessParser] that takes one argument.
 */
public class NamedUnaryLexerlessParser<ArgumentT> internal constructor(
    override val name: String,
    internal val unnamed: UnaryLexerlessParser<ArgumentT>
) : NamedUnaryParser<ArgumentT>, UnaryParser<ArgumentT> by unnamed {
    init {
        debug { "Named at ${unnamed.id.toString(radix = 16)}" }
    }

    override fun toString(): String = name
}

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
public sealed class NameableLexerParser(def: LexerParserDefinition) : Lexer, Parser {
    private val skip = def.skip.asSequence().map { it.name }.toImmutableSet()   // Copy ASAP

    internal val parserSymbols = def.resolveSymbols()
    internal val listeners = def.listeners
    internal val id = hashCode()

    private val lexerSymbols = def.lexerSymbols
    private val start = def.startDelegate.field
    private val recovery = def.recoveryDelegate.field

    init {
        def.inversionSymbols.forEach { it.origin = this }
        debug { "Parser $id: Defined with parser symbols $parserSymbols" }
        debug { "Parser $id: Defined with lexer symbols $lexerSymbols" }
    }

    final override val symbols: Map<String, NameableSymbol<*>> by lazy {
        HashMap<String, NameableSymbol<*>>(parserSymbols.size + lexerSymbols.size).apply {
            putAll(parserSymbols)
            putAll(lexerSymbols.associate { it.name to it.unnamed })
        }.toImmutableMap()
    }

    final override fun parse(input: String): Node<*>? = parse(input.pivotIterator())
    final override fun parse(input: RawSource): Node<*>? = parse(input.pivotIterator())

    // Defensive copy
    final override fun tokenize(input: String): List<Token> = tokenize(input.pivotIterator()).toList()
    final override fun tokenize(input: RawSource): List<Token> = tokenize(input.pivotIterator()).toList()

    final override fun toString(): String = "Lexer-parser ${id.toString(radix = 16)}"

    private fun parse(input: CharPivotIterator): Node<*>? {
        return (start ?: raiseUndefinedStart()).match(ParserMetadata(tokenize(input).pivotIterator()))
    }

    private fun tokenize(input: CharPivotIterator): List<Token> {
        val metadata = ParserMetadata(input)
        val tokens = mutableListOf<Token>()
        var inRecovery = false
        var recoveryIndex = 0
        while (input.hasNext()) {
            val tokenNode = lexerSymbols.asSequence().mapNotNull { it.match(metadata) }.firstOrNull()
            if (tokenNode == null) { // Attempt error recovery
                if (!inRecovery) {
                    inRecovery = true
                    recoveryIndex = tokens.size
                }
                val recoveryNode = recovery?.match(metadata)?.takeIf { it.substring.isNotEmpty() }
                tokens += Token("", recoveryNode?.substring ?: throw IllegalTokenException(tokens))
            } else {    // Successfully matched to named lexer symbol
                if (inRecovery) {
                    val recoveryTokens = tokens.subList(recoveryIndex, tokens.lastIndex)
                    val concatenated = Token("", recoveryTokens.asSequence().map { it.substring }.joinToString(""))
                    recoveryTokens.clear()
                    tokens += concatenated
                }
                if (tokenNode.toString() in skip) {
                    continue
                }
            }
        }
        return tokens
    }
}

/**
 * A [NameableLexerParser] that does not take an argument.
 */
public class NullaryLexerParser internal constructor(
    def: NullaryLexerParserDefinition
) : NameableLexerParser(def), Nameable, NullaryParser, Lexer {
    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>
    ): ReadOnlyProperty<Nothing?, NamedNullaryLexerParser> {
        return NamedNullaryLexerParser(property.name, this).readOnlyProperty()
    }
}

/**
 * A named [NameableLexerParser] that does not take an argument.
 */
public class NamedNullaryLexerParser internal constructor(
    override val name: String,
    internal val unnamed: NullaryLexerParser
) : NamedNullaryParser, NullaryParser by unnamed, Lexer by unnamed {
    init {
        debug { "Named at ${unnamed.id.toString(radix = 16)}" }
    }

    override fun toString(): String = name
}

/**
 * A [NameableLexerParser] that takes one argument.
 */
public class UnaryLexerParser<ArgumentT> internal constructor(
    def: UnaryLexerParserDefinition<ArgumentT>
) : NameableLexerParser(def), Nameable, UnaryParser<ArgumentT>, Lexer {
    internal val initializer = def.base.initializer

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>
    ): ReadOnlyProperty<Nothing?, NamedUnaryLexerParser<ArgumentT>> {
        return NamedUnaryLexerParser(property.name, this).readOnlyProperty()
    }
}

/**
 * A named [NameableLexerParser] that takes one argument.
 */
public class NamedUnaryLexerParser<ArgumentT> internal constructor(
    override val name: String,
    internal val unnamed: UnaryLexerParser<ArgumentT>
) : NamedUnaryParser<ArgumentT>, UnaryParser<ArgumentT> by unnamed, Lexer by unnamed {
    init {
        debug { "Named at ${unnamed.id.toString(radix = 16)}" }
    }

    override fun toString(): String = name
}