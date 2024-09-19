@file:JvmName("Parsers")
package io.github.aeckar.parsing

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmName

/**
 * Creates a new [LexerlessParser].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
@OptIn(ExperimentalContracts::class)
public fun parser(definition: LexerlessParserDefinition.() -> Unit): LexerlessParser {
    contract { callsInPlace(definition, InvocationKind.EXACTLY_ONCE) }
    return LexerlessParser(LexerlessParserDefinition().apply(definition))
}

/**
 * Creates a new [LexerlessParserOperator].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
@OptIn(ExperimentalContracts::class)
public fun <R> parserOperator(
    definition: LexerlessParserOperatorDefinition<R>.() -> Unit
): LexerlessParserOperator<R> {
    contract { callsInPlace(definition, InvocationKind.EXACTLY_ONCE) }
    return LexerlessParserOperator(LexerlessParserOperatorDefinition<R>().also { definition(it) })
}

/**
 * Creates a new [LexerParser].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
@OptIn(ExperimentalContracts::class)
public fun lexerParser(definition: LexerParserDefinition.() -> Unit): LexerParser {
    contract { callsInPlace(definition, InvocationKind.EXACTLY_ONCE) }
    return LexerParser(LexerParserDefinition().apply(definition))
}

/**
 * Creates a new [LexerParserOperator].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
@OptIn(ExperimentalContracts::class)
public fun <R> lexerParserOperator(
    definition: LexerParserOperatorDefinition<R>.() -> Unit
): LexerParserOperator<R> {
    contract { callsInPlace(definition, InvocationKind.EXACTLY_ONCE) }
    return LexerParserOperator(LexerParserOperatorDefinition<R>().also { definition(it) })
}