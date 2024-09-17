package io.github.aeckar.parsing

import io.github.aeckar.parsing.utils.*
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.io.RawSource

/**
 * Evaluates an input to some value of type [R].
 */
public sealed interface Operator<R>

/**
 * Evaluates [input] to some value by in.
 *
 * Invokes the listeners of this parser on the nodes of a
 * completed [abstract syntax tree][Parser.parse] representing the given input.
 */
public operator fun <R> Operator<R>.invoke(input: String): R {
    val parser = this as Parser
    parser.parse(input)?.invokeListeners()
    return parser.returnValue!!().unsafeCast()
}

/**
 * Evaluates [input] to some value.
 *
 * Invokes the listeners of this parser on the nodes of a
 * completed [abstract syntax tree][Parser.parse] representing the given input.
 */
public operator fun <R> Operator<R>.invoke(input: RawSource): R {
    val parser = this as Parser
    parser.parse(input)?.invokeListeners()
    return parser.returnValue!!().unsafeCast()
}

/**
 * An operator capable of evaluating a list of tokens.
 */
public sealed interface LexerOperator<R>

public operator fun <R> LexerOperator<R>.invoke(input: List<Token>): R {
    val parser = this as LexerParser
    parser.parse(input)?.invokeListeners()
    return parser.returnValue!!().unsafeCast()
}

/**
 * Performs actions according to a formal grammar.
 *
 * Delegating an instance of this class to a property produces a named wrapper of that instance,
 * enabling the [import][ParserDefinition.import] of named symbols from a parser.
 */
public sealed class Parser(def: ParserDefinition) {
    internal val parserSymbols = def.compileSymbols()
    internal val returnValue: (() -> Any?)? = def.returnValueDelegate.field
    internal val listenersMap = def.listeners

    /**
     * An immutable map of all symbols defined in this parser.
     */
    public abstract val symbols: Map<String, NameableSymbol<*>>

    /**
     * An immutable map of all listeners defined in this parser.
     */
    public val listeners: Map<String, ParserDefinition.Listener<*>> by lazy { listenersMap.toImmutableMap() }

    private val start = def.startDelegate.field

    init {
        def.inversionSymbols.forEach { it.origin = this }
        debug { "Defined with parser symbols $parserSymbols" }
    }

    /**
     * Returns the root node of the abstract syntax tree representing the input, if one exists.
     */
    public abstract fun parse(input: String): SyntaxTreeNode<*>?

    /**
     * Returns the root node of the abstract syntax tree representing the input, if one exists.
     */
    public abstract fun parse(input: RawSource): SyntaxTreeNode<*>?

    internal fun resolveStart(): Symbol {
        if (start != null) {
            return start
        }
        throw MalformedParserException("Start symbol is undefined")
    }

    @PublishedApi
    internal fun SyntaxTreeNode<*>.invokeListeners(): SyntaxTreeNode<*> = onEach {
        listenersMap[it.toString()]
            ?.unsafeCast<ParserDefinition.Listener<Symbol>>()
            ?.apply { it.unsafeCast<SyntaxTreeNode<Symbol>>()() }
    }
}

///**
// * Invokes the listeners of this parser on the nodes of a
// * completed [abstract syntax tree][Parser.parse] representing the given input.
// *
// * @return the root node of the resulting AST
// */



/**
 * A parser that parses its input directly.
 */
public open class LexerlessParser internal constructor(def: LexerlessParserDefinition) : Parser(def) {
    internal val skip = def.skipDelegate.field

    override val symbols: Map<String, NameableSymbol<*>> by lazy { parserSymbols.toImmutableMap() }

    override fun parse(input: String): SyntaxTreeNode<*>? = parse(input.inputIterator())
    override fun parse(input: RawSource): SyntaxTreeNode<*>? = parse(input.inputIterator())

    private fun parse(input: InputIterator<*, *>): SyntaxTreeNode<*>? {
        return resolveStart().match(ParsingAttempt(input, skip))
    }
}

/**
 * A parser that parses its input directly, and is capable of evaluating it to some instance of type [R].
 */
public class LexerlessParserOperator<R> internal constructor(
    def: LexerlessParserDefinition
) : LexerlessParser(def), Operator<R>

/**
 * A parser that tokenizes its input before parsing.
 */
public open class LexerParser internal constructor(def: LexerParserDefinition) : Parser(def) {
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
        debug { "Parser Defined with lexer modes $lexerModes" }
    }

    /**
     * Returns the root node of the abstract syntax tree representing the input, if one exists.
     */
    public fun parse(input: List<Token>): SyntaxTreeNode<*>? {  // TODO test to remove warning
        return resolveStart().match(ParsingAttempt(input.inputIterator(), null))
    }

    /**
     * Returns a list of tokens representing the input.
     */
    public fun tokenize(input: String): List<Token> = tokenize(input.inputIterator()).toList()

    /**
     * Returns a list of tokens representing the input.
     */
    public fun tokenize(input: RawSource): List<Token> = tokenize(input.inputIterator()).toList()

    override fun parse(input: String): SyntaxTreeNode<*>? = parse(tokenize(input.inputIterator()))
    override fun parse(input: RawSource): SyntaxTreeNode<*>? = parse(tokenize(input.inputIterator()))

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
 * A parser that tokenizes its input before parsing, and is capable of evaluating it to an instance of type [R].
 */
public class LexerParserOperator<R> internal constructor(
    def: LexerParserDefinition
) : LexerParser(def), LexerOperator<R>