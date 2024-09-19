package io.github.aeckar.parsing

import io.github.aeckar.parsing.utils.*
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.io.RawSource
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/*
    ---- class hierarchy ----
    Operator (i)
        LexerOperator (i)

    Parser (i)
        NameableParser (sc)
        LexerlessParser (i)
            NameableLexerlessParser : NameableParser (oc)
                NameableLexerlessOperator : Operator (c)
        LexerParser (i)
            NameableLexerParser : NameableParser (oc)
                NameableLexerOperator : LexerOperator (c)
        NamedParser : Named (i)
            NamedLexerlessParser : LexerlessParser (oc)
                NamedLexerlessOperator : Operator (c)
            NamedLexerParser : LexerParser (oc)
                NamedLexerOperator : LexerOperator (c)
 */

// ------------------------------------ operators ------------------------------------

/**
 * Evaluates an input to some value of type [R].
 */
public sealed interface Operator<R>

/**
 * Evaluates [input] to a value.
 *
 * Invokes the listeners of this parser on the nodes of a
 * completed [abstract syntax tree][Parser.parse] representing the given input.
 */
public operator fun <R> Operator<R>.invoke(input: String): R {
    (this as Parser).parse(input)?.invokeListeners(this)
    return operator<R>().returnValue()
}

/**
 * Evaluates [input] to a value.
 *
 * Invokes the listeners of this parser on the nodes of a
 * completed [abstract syntax tree][Parser.parse] representing the given input.
 */
public operator fun <R> Operator<R>.invoke(input: RawSource): R {
    (this as Parser).parse(input)?.invokeListeners(this)
    return operator<R>().returnValue()
}

/**
 * An operator capable of evaluating a list of tokens.
 */
public sealed interface LexerOperator<R> : Lexer, Operator<R>

/**
 * Evaluates [input] to a value.
 *
 * Invokes the listeners of this parser on the nodes of a
 * completed [abstract syntax tree][LexerParser.parse] representing the given input.
 */
public operator fun <R> LexerOperator<R>.invoke(input: List<Token>): R {
    (this as LexerParser).parse(input)?.invokeListeners(this)
    return operator<R>().returnValue()
}

// ------------------------------------ parser-operators  ------------------------------------

internal class OperatorProperties<R>(def: OperatorDefinition<R>) {
    private val operator = (def as ParserDefinition).operator

    internal val listenersMap = operator.listeners
    internal val listenersCopy by lazy { listenersMap.toImmutableMap() }
    internal val returnValue = operator.returnValueDelegate.field.unsafeCast() as? () -> R
        ?: throw MalformedParserException("Undefined return value")
}

@PublishedApi
internal fun SyntaxTreeNode<*>.invokeListeners(parser: Parser): SyntaxTreeNode<*> = onEach {
    parser.operator<Any?>().listenersMap[it.toString()]
        ?.unsafeCast<Listener<Symbol>>()
        ?.apply { it.unsafeCast<SyntaxTreeNode<Symbol>>()() }
}

// ------------------------------------ parsers & lexers ------------------------------------

/**
 * Performs actions according to a formal grammar.
 */
public sealed interface Parser {
    /**
     * An immutable map of all symbols defined in this parser.
     */
    public val symbols: Map<String, NameableSymbol<*>>

    /**
     * Returns the root node of the abstract syntax tree representing the input, if one exists.
     */
    public fun parse(input: String): SyntaxTreeNode<*>?

    /**
     * Returns the root node of the abstract syntax tree representing the input, if one exists.
     */
    public fun parse(input: RawSource): SyntaxTreeNode<*>?
}

internal fun <R> Parser.operator(): OperatorProperties<R> = when (this) {
    is NameableParser -> operator.unsafeCast()
    is NamedParser -> unnamed.operator.unsafeCast()
}

/**
 * A nameable parser.
 */
public sealed class NameableParser(def: ParserDefinition) : Nameable, Parser {
     internal val unnamedLogger = @Suppress("LeakingThis") logger("parser@${hashCode().toString(radix = 8)}")
    internal val parserSymbols = def.compileSymbols()
    internal open val operator: OperatorProperties<*> get() = raiseNonOperator()

    /**
     * An immutable map of all listeners defined in this parser.
     */
    internal open val listeners: Map<String, Listener<*>> get() = raiseNonOperator()

    private val start = def.startDelegate.field

    init {
        def.inversionSymbols.forEach { it.origin = this }
        topLevel.debugAt(@Suppress("LeakingThis") this) { "Defined with parser symbols $parserSymbols" }
    }

    internal fun start(): Symbol {
        if (start != null) {
            return start
        }
        throw MalformedParserException("Start symbol is undefined")
    }
}

/**
 * A named parser.
 */
public sealed class NamedParser(
    override val name: String
) : Named, Parser {
    internal abstract val unnamed: NameableParser
    internal val namedLogger = logger(@Suppress("LeakingThis") name)

    /**
     * An immutable map of all listeners defined in this parser.
     */
    protected open val listeners: Map<String, Listener<*>> get() = unnamed.listeners
}

/**
 * Transforms input into a list of [Token]s.
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
public sealed interface LexerParser : Lexer, Parser {
    /**
     * Returns the root node of the abstract syntax tree representing the input, if one exists.
     */
    public fun parse(input: List<Token>): SyntaxTreeNode<*>?
}

// ------------------------------------ lexerless parsers ------------------------------------

/**
 * A nameable parser that parses its input directly.
 */
public open class NameableLexerlessParser internal constructor(
    def: LexerlessParserDefinition
) : NameableParser(def) {
    internal val skip = def.skipDelegate.field

    internal fun parse(logger: KLogger, input: InputIterator<*, *>): SyntaxTreeNode<*>? {
        return start().match(ParsingAttempt(logger, input, skip))
    }

    override val symbols: Map<String, NameableSymbol<*>> by lazy { parserSymbols.toImmutableMap() }

    override fun parse(input: String): SyntaxTreeNode<*>? = parse(unnamedLogger, input.inputIterator())
    override fun parse(input: RawSource): SyntaxTreeNode<*>? = parse(unnamedLogger, input.inputIterator())

    override fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Named> {
        return NamedLexerlessParser(property.name, this).readOnlyProperty()
    }
}

/**
 * A parser that parses its input directly, and is capable of evaluating it to some instance of type [R].
 */
public class NameableLexerlessParserOperator<R> internal constructor(
    def: LexerlessParserDefinition
) : NameableLexerlessParser(def), Operator<R> {
    public override val listeners: Map<String, Listener<*>> get() = operator.listenersCopy
}

/**
 * A named lexerless parser.
 */
public open class NamedLexerlessParser internal constructor(
    name: String,
    override val unnamed: NameableLexerlessParser
) : NamedParser(name) {
    final override val name: String get() = super.name
    final override val symbols: Map<String, NameableSymbol<*>> get() = unnamed.symbols

    override fun parse(input: String): SyntaxTreeNode<*>? {
        return unnamed.parse(namedLogger, input.inputIterator())
    }

    override fun parse(input: RawSource): SyntaxTreeNode<*>? {
        return unnamed.parse(namedLogger, input.inputIterator())
    }
}

/**
 * A named lexerless parser-operator.
 */
public class NamedLexerlessParserOperator<R> internal constructor(
    name: String,
    unnamed: NameableLexerlessParserOperator<R>
) : NamedLexerlessParser(name, unnamed), Operator<R> {
    public override val listeners: Map<String, Listener<*>> get() = super.listeners
}

// ------------------------------------ lexer-parsers ------------------------------------

/**
 * A nameable lexer-parser
 */
public open class NameableLexerParser internal constructor(
    def: LexerParserDefinition
) : NameableParser(def), LexerParser {
    private val skip = def.skip.asSequence().map { it.name }.toImmutableSet()   // Copy ASAP

    private val lexerModes: Map<String, List<NamedSymbol<LexerSymbol>>> = def.lexerModes
    private val recovery = (def.recoveryDelegate.field as? ParserComponent)?.unwrap()

    override val symbols: Map<String, NameableSymbol<*>> by lazy {
        buildMap(parserSymbols.size + lexerModes.values.sumOf { it.size }) {
            putAll(parserSymbols)
            for (mode in lexerModes.values) {
                mode.forEach { put(it.name, it.unnamed) }
            }
        }
    }

    init {
        topLevel.debugAt(@Suppress("LeakingThis") this) { "Parser Defined with lexer modes $lexerModes" }
    }

    internal fun parse(logger: KLogger, input: List<Token>): SyntaxTreeNode<*>? {
        return start().match(ParsingAttempt(logger, input.inputIterator(), null))
    }

    internal fun tokenize(logger: KLogger, input: InputIterator<*, *>): List<Token> {
        val metadata = ParsingAttempt(logger, input, null)
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

    override fun tokenize(input: String): List<Token> = tokenize(unnamedLogger, input.inputIterator())
    override fun tokenize(input: RawSource): List<Token> = tokenize(unnamedLogger, input.inputIterator())
    override fun parse(input: String): SyntaxTreeNode<*>? = parse(tokenize(input))
    override fun parse(input: RawSource): SyntaxTreeNode<*>? = parse(tokenize(input))

    override fun parse(input: List<Token>): SyntaxTreeNode<*>? {
        return parse(unnamedLogger, input)
    }

    override fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Named> {
        return NamedLexerParser(property.name, this).readOnlyProperty()
    }
}

/**
 * A parser that tokenizes its input before parsing, and is capable of evaluating it to an instance of type [R].
 */
public class NameableLexerParserOperator<R> internal constructor(
    def: LexerParserDefinition
) : NameableLexerParser(def), LexerOperator<R> {
    public override val listeners: Map<String, Listener<*>> get() = operator.listenersCopy
}

/**
 * A named lexer-parser.
 */
public open class NamedLexerParser internal constructor(
    name: String,
    override val unnamed: NameableLexerParser
) : NamedParser(name), LexerParser {
    final override val name: String get() = super.name
    final override val symbols: Map<String, NameableSymbol<*>> get() = unnamed.symbols

    override fun parse(input: String): SyntaxTreeNode<*>? {
        return unnamed.parse(namedLogger, tokenize(input))
    }

    override fun parse(input: RawSource): SyntaxTreeNode<*>? {
        return unnamed.parse(namedLogger, tokenize(input))
    }

    override fun parse(input: List<Token>): SyntaxTreeNode<*>? {
        return unnamed.parse(namedLogger, input)
    }

    override fun tokenize(input: String): List<Token> {
        return unnamed.tokenize(namedLogger, input.inputIterator())
    }

    override fun tokenize(input: RawSource): List<Token> {
        return unnamed.tokenize(namedLogger, input.inputIterator())
    }
}

/**
 * A named lexer-parser-operator
 */
public class NamedLexerParserOperator<R> internal constructor(
    name: String,
    unnamed: NameableLexerParserOperator<R>
) : NamedLexerParser(name, unnamed), LexerOperator<R> {
    public override val listeners: Map<String, Listener<*>> get() = super.listeners
}