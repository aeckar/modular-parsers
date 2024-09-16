@file:JvmName("ParserFactories")
package io.github.aeckar.parsing

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmName

/**
 * Creates a new, nameable [LexerlessParser].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
@OptIn(ExperimentalContracts::class)
public fun parser(definition: NullaryLexerlessParserDefinition.() -> Unit): LexerlessParser {
    contract { callsInPlace(definition, InvocationKind.EXACTLY_ONCE) }
    return LexerlessParser(NullaryLexerlessParserDefinition().apply(definition))
}

/**
 * Creates a new, nameable [LexerlessParserOperator].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
@OptIn(ExperimentalContracts::class)
public fun <R> parser(definition: NullaryLexerlessParserDefinition.() -> ReturnDescriptor<R>): LexerlessParserOperator {
    contract { callsInPlace(definition, InvocationKind.EXACTLY_ONCE) }
    return LexerlessParserOperator(NullaryLexerlessParserDefinition().apply(definition))
}

/**
 * Creates a new, nameable [NameableLexerParser].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
@OptIn(ExperimentalContracts::class)
public fun lexerParser(definition: NullaryLexerParserDefinition.() -> Unit): LexerParser {
    contract { callsInPlace(definition, InvocationKind.EXACTLY_ONCE) }
    return LexerParser(NullaryLexerParserDefinition().apply(definition))
}

/**
 * Creates a new, nameable [LexerParserOperator].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
@OptIn(ExperimentalContracts::class)
public fun <R> lexerParser(definition: NullaryLexerParserDefinition.() -> ReturnDescriptor<R>): LexerParserOperator {
    contract { callsInPlace(definition, InvocationKind.EXACTLY_ONCE) }
    return LexerParserOperator(NullaryLexerParserDefinition().apply(definition))
}