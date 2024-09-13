package io.github.aeckar.parsing

import io.github.aeckar.parsing.utils.*
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.io.RawSource
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// ------------------------------ generic parsers ------------------------------

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
     * An immutable map of all listeners defined in this parser.
     */
    public val listeners: Map<String, SymbolListener>

    /**
     * Returns the root node of the abstract syntax tree representing the input, if one exists.
     */
    public fun parse(input: String): SyntaxTreeNode<*>?

    /**
     * See [parse] for details.
     */
    public fun parse(input: RawSource): SyntaxTreeNode<*>?
}

internal fun Parser.resolveSymbols(): Map<String, NameableSymbol<*>> = when (this) {
    is NameableLexerlessParser -> parserSymbols
    is NameableLexerParser -> parserSymbols
    is NamedNullaryLexerlessParser -> unnamed.parserSymbols
    is NamedUnaryLexerlessParser<*> -> unnamed.parserSymbols
    is NamedNullaryLexerParser -> unnamed.parserSymbols
    is NamedUnaryLexerParser<*> -> unnamed.parserSymbols
}

internal fun Parser.resolveListeners() = when (this) {
    is NameableLexerlessParser -> symbolListeners
    is NameableLexerParser -> symbolListeners
    is NamedNullaryLexerlessParser -> unnamed.symbolListeners
    is NamedUnaryLexerlessParser<*> -> unnamed.symbolListeners
    is NamedNullaryLexerParser -> unnamed.symbolListeners
    is NamedUnaryLexerParser<*> -> unnamed.symbolListeners
}

/**
 * @throws MalformedParserException always
 */
private fun Parser.raiseUndefinedStart(): Nothing {
    val name = if (this is Named) name  else ""
    throw MalformedParserException("Start symbol for parser '$name' is undefined")
}

/**
 * A named parser.
 */
public sealed interface NamedParser : Named, Parser

// ------------------------------ nullary parsers ------------------------------

/**
 * A parser that does not take an argument.
 */
public sealed interface NullaryParser : Parser {
    abstract override val listeners: Map<String, NullarySymbolListener<*>>
}

/**
 * Invokes the listeners of this parser on the nodes of a
 * completed [abstract syntax tree][Parser.parse] representing the given input.
 *
 * @return the root node of the resulting AST
 */
public operator fun NullaryParser.invoke(input: String): SyntaxTreeNode<*>? {
    val ast = parse(input)
    ast?.forEach(::invokeListener)
    return ast
}

/**
 * See [NullaryParser.invoke] for details.
 */
public operator fun NullaryParser.invoke(input: RawSource): SyntaxTreeNode<*>? {
    val ast = parse(input)
    ast?.forEach(::invokeListener)
    return ast
}

private fun NullaryParser.invokeListener(node: SyntaxTreeNode<*>) {
    listeners[node.toString()]
        ?.unsafeCast<NullarySymbolListener<Symbol>>()
        ?.apply { node.unsafeCast<SyntaxTreeNode<Symbol>>()() }
}

/**
 * A named nullary parser.
 */
public sealed interface NamedNullaryParser : NamedParser, NullaryParser

// ------------------------------ unary parsers ------------------------------

/**
 * A parser that takes one argument.
 */
public sealed interface UnaryParser<in ArgumentT> : Parser {
    abstract override val listeners: Map<String, UnarySymbolListener<*, ArgumentT>>
}

/**
 * Invokes the listeners of this parser on the nodes of a
 * completed [abstract syntax tree][Parser.parse] representing the given input.
 *
 * The argument passed to this function is also passed to each listener.
 * @return the root node of the resulting AST
 */
public operator fun <ArgumentT> UnaryParser<ArgumentT>.invoke(argument: ArgumentT, input: String): SyntaxTreeNode<*>? {
    resolveInitializer()?.let { it(argument) }
    val ast = parse(input)
    ast?.forEach { invokeListener(argument, it) }
    return ast
}

/**
 * See [UnaryParser.invoke] for details.
 */
public operator fun <ArgumentT> UnaryParser<ArgumentT>.invoke(argument: ArgumentT, input: RawSource): SyntaxTreeNode<*>? {
    resolveInitializer()?.let { it(argument) }
    val ast = parse(input)
    ast?.forEach { invokeListener(argument, it) }
    return ast
}

private fun <ArgumentT> UnaryParser<ArgumentT>.resolveInitializer() = when (this) {
    is UnaryLexerlessParser<ArgumentT> -> initializer
    is UnaryLexerParser<ArgumentT> -> initializer
    is NamedUnaryLexerParser -> unnamed.initializer
    is NamedUnaryLexerlessParser -> unnamed.initializer
}

private fun <ArgumentT> UnaryParser<ArgumentT>.invokeListener(argument: ArgumentT, node: SyntaxTreeNode<*>) {
    resolveListeners()[node.toString()]
        ?.unsafeCast<UnarySymbolListener<Symbol, ArgumentT>>()
        ?.apply { node.unsafeCast<SyntaxTreeNode<Symbol>>()(argument) }
}

/**
 * A named unary parser.
 */
public sealed interface NamedUnaryParser<in ArgumentT> : NamedParser, UnaryParser<ArgumentT>

// ------------------------------ lexerless parsers ------------------------------

/**
 * A parser that creates an abstract syntax tree directly from its input.
 */
public sealed interface LexerlessParser : Parser

internal fun LexerlessParser.resolveSkip() = when (this) {
    is NameableLexerlessParser -> skip
    is NamedNullaryLexerlessParser -> unnamed.skip
    is NamedUnaryLexerlessParser<*> -> unnamed.skip
}

/**
 * A nameable lexerless parser.
 */
public sealed class NameableLexerlessParser(def: LexerlessParserDefinition) : Nameable, LexerlessParser {
    internal val parserSymbols = def.compileSymbols()
    internal val skip = def.skipDelegate.field
    internal open val symbolListeners = def.resolveListeners()
    @Suppress("LeakingThis") internal val id = hashCode()

    private val start = def.startDelegate.field

    final override val symbols: Map<String, NameableSymbol<*>> by lazy { parserSymbols.toImmutableMap() }

    init {
        def.inversionSymbols.forEach { it.origin = this }
        debug { "Defined with parser symbols $parserSymbols" }
    }

    final override fun parse(input: String): SyntaxTreeNode<*>? = parse(input.inputIterator())
    final override fun parse(input: RawSource): SyntaxTreeNode<*>? = parse(input.inputIterator())
    final override fun toString(): String = "Parser ${id.toString(radix = 16)}"

    private fun parse(input: InputIterator<*, *>): SyntaxTreeNode<*>? {
        return (start ?: raiseUndefinedStart()).match(ParsingAttempt(input, skip))
    }
}

/**
 * A lexerless parser that takes no arguments.
 */
public class NullaryLexerlessParser internal constructor(
    def: NullaryLexerlessParserDefinition
) : NameableLexerlessParser(def), NullaryParser {
    override val symbolListeners: Map<String, NullarySymbolListener<*>> get() = super.symbolListeners.unsafeCast()
    override val listeners: Map<String, NullarySymbolListener<*>> by lazy { symbolListeners.toImmutableMap() }

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>
    ): ReadOnlyProperty<Any?, NamedNullaryLexerlessParser> {
        return NamedNullaryLexerlessParser(property.name, this).readOnlyProperty()
    }
}

/**
 * A named lexerless parser that does not take an argument.
 */
public class NamedNullaryLexerlessParser internal constructor(
    override val name: String,
    internal val unnamed: NullaryLexerlessParser
) : NamedNullaryParser, NullaryParser by unnamed, LexerlessParser {
    init {
        debug { "Named parser ${unnamed.id.toString(radix = 16)}" }
    }

    override fun toString(): String = name
}

/**
 * A lexerless parser that takes an argument.
 */
public class UnaryLexerlessParser<in ArgumentT> internal constructor(
    def: UnaryLexerlessParserDefinition<ArgumentT>
) : NameableLexerlessParser(def), UnaryParser<ArgumentT> {
    internal val initializer = def.initializer

    override val symbolListeners: Map<String, UnarySymbolListener<*, ArgumentT>>
        get() = super.symbolListeners.unsafeCast()
    override val listeners: Map<String, UnarySymbolListener<*, ArgumentT>> by lazy { symbolListeners.toImmutableMap() }

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>
    ): ReadOnlyProperty<Any?, NamedUnaryLexerlessParser<ArgumentT>> {
        return NamedUnaryLexerlessParser(property.name, this).readOnlyProperty()
    }
}

/**
 * A named lexerless parser that takes one argument.
 */
public class NamedUnaryLexerlessParser<in ArgumentT> internal constructor(
    override val name: String,
    internal val unnamed: UnaryLexerlessParser<ArgumentT>
) : NamedUnaryParser<ArgumentT>, UnaryParser<ArgumentT> by unnamed, LexerlessParser {
    init {
        debug { "Named parser ${unnamed.id.toString(radix = 16)}" }
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
     * See [tokenize] for details.
     */
    public fun tokenize(input: RawSource): List<Token>
}

/**
 * A parser that tokenizes its input before parsing.
 */
public sealed interface LexerParser : Lexer, Parser {
    /**
     * See [parse] for details.
     */
    public fun parse(input: List<Token>): SyntaxTreeNode<*>?
}

/**
 * A nameable lexer-parser.
 */
public sealed class NameableLexerParser(def: LexerParserDefinition) : Nameable, LexerParser {
    private val skip = def.skip.asSequence().map { it.name }.toImmutableSet()   // Copy ASAP

    internal val parserSymbols = def.compileSymbols()
    internal open val symbolListeners = def.resolveListeners()
    internal val id = def.hashCode()

    private val lexerModes: Map<String, List<NamedSymbol<LexerSymbol>>> = def.lexerModes
    private val start = def.startDelegate.field
    private val recovery = (def.recoveryDelegate.field as? ParserComponent)?.unwrap()

    init {
        def.inversionSymbols.forEach { it.origin = this }
        debug { "Parser $id: Defined with parser symbols $parserSymbols" }
        debug { "Parser $id: Defined with lexer modes $lexerModes" }
    }

    final override val symbols: Map<String, NameableSymbol<*>> by lazy {
        buildMap(parserSymbols.size + lexerModes.values.sumOf { it.size }) {
            putAll(parserSymbols)
            for (mode in lexerModes.values) {
                mode.forEach { put(it.name, it.unnamed) }
            }
        }
    }

    final override fun parse(input: String): SyntaxTreeNode<*>? = parse(tokenize(input.inputIterator()))
    final override fun parse(input: RawSource): SyntaxTreeNode<*>? = parse(tokenize(input.inputIterator()))

    final override fun parse(input: List<Token>): SyntaxTreeNode<*>? {
        return (start ?: raiseUndefinedStart()).match(ParsingAttempt(input.inputIterator(), null))
    }

    // Defensive copy
    final override fun tokenize(input: String): List<Token> = tokenize(input.inputIterator()).toList()
    final override fun tokenize(input: RawSource): List<Token> = tokenize(input.inputIterator()).toList()

    final override fun toString(): String = "Lexer-parser ${id.toString(radix = 16)}"

    private fun tokenize(input: InputIterator<*, *>): List<Token> {
        val metadata = ParsingAttempt(input, null)
        val tokens = mutableListOf<Token>()
        var inRecovery = false
        var recoveryIndex = 0
        while (input.hasNext()) {
            val tokenNode = lexerModes.getValue(metadata.modeStack.last()).firstNotNullOfOrNull { it.match(metadata) }
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
) : NameableLexerParser(def), NullaryParser, LexerParser {
    override val symbolListeners: Map<String, NullarySymbolListener<*>> get() = super.symbolListeners.unsafeCast()
    override val listeners: Map<String, NullarySymbolListener<*>> by lazy { symbolListeners.toImmutableMap() }

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>
    ): ReadOnlyProperty<Any?, NamedNullaryLexerParser> {
        return NamedNullaryLexerParser(property.name, this).readOnlyProperty()
    }
}

/**
 * A named [NameableLexerParser] that does not take an argument.
 */
public class NamedNullaryLexerParser internal constructor(
    override val name: String,
    internal val unnamed: NullaryLexerParser
) : NamedNullaryParser, NullaryParser, LexerParser by unnamed {
    override val listeners: Map<String, NullarySymbolListener<*>> get() = unnamed.listeners

    init {
        debug { "Named parser ${unnamed.id.toString(radix = 16)}" }
    }

    override fun toString(): String = name
}

/**
 * A [NameableLexerParser] that takes one argument.
 */
public class UnaryLexerParser<in ArgumentT> internal constructor(
    def: UnaryLexerParserDefinition<ArgumentT>
) : NameableLexerParser(def), UnaryParser<ArgumentT> {
    internal val initializer = def.lexerless.initializer

    override val symbolListeners: Map<String, UnarySymbolListener<*, ArgumentT>>
        get() = super.symbolListeners.unsafeCast()
    override val listeners: Map<String, UnarySymbolListener<*, ArgumentT>> by lazy { symbolListeners.toImmutableMap() }

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>
    ): ReadOnlyProperty<Any?, NamedUnaryLexerParser<ArgumentT>> {
        return NamedUnaryLexerParser(property.name, this).readOnlyProperty()
    }
}

/**
 * A named [NameableLexerParser] that takes one argument.
 */
public class NamedUnaryLexerParser<in ArgumentT> internal constructor(
    override val name: String,
    internal val unnamed: UnaryLexerParser<ArgumentT>
) : NamedUnaryParser<ArgumentT>, UnaryParser<ArgumentT>, LexerParser by unnamed {
    override val listeners: Map<String, UnarySymbolListener<*, ArgumentT>> get() = unnamed.listeners

    init {
        debug { "Named parser ${unnamed.id.toString(radix = 16)}" }
    }

    override fun toString(): String = name
}