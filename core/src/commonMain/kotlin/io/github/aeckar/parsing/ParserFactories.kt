@file:JvmName("Parsers")
package io.github.aeckar.parsing

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmName

/**
 * Creates a new [lexerless parser][NameableLexerlessParser].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
@OptIn(ExperimentalContracts::class)
public fun parser(definition: LexerlessParserDefinition.() -> Unit): NameableLexerlessParser {
    contract { callsInPlace(definition, InvocationKind.EXACTLY_ONCE) }
    return NameableLexerlessParser(LexerlessParserDefinition().apply(definition))
}

/**
 * Creates a new, [lexerless parser-operator][NameableLexerlessParserOperator].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
@OptIn(ExperimentalContracts::class)
public fun <R> parserOperator(
    definition: LexerlessParserOperatorDefinition<R>.() -> Unit
): NameableLexerlessParserOperator<R> {
    contract { callsInPlace(definition, InvocationKind.EXACTLY_ONCE) }
    return NameableLexerlessParserOperator(LexerlessParserOperatorDefinition<R>().also { definition(it) })
}

/**
 * Creates a new [lexer-parser][LexerParser].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
@OptIn(ExperimentalContracts::class)
public fun lexerParser(definition: LexerParserDefinition.() -> Unit): NameableLexerParser {
    contract { callsInPlace(definition, InvocationKind.EXACTLY_ONCE) }
    return NameableLexerParser(LexerParserDefinition().apply(definition))
}

/**
 * Creates a new [lexer-parser-operator][NameableLexerParserOperator].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
@OptIn(ExperimentalContracts::class)
public fun <R> lexerParserOperator(
    definition: LexerParserOperatorDefinition<R>.() -> Unit
): NameableLexerParserOperator<R> {
    contract { callsInPlace(definition, InvocationKind.EXACTLY_ONCE) }
    return NameableLexerParserOperator(LexerParserOperatorDefinition<R>().also { definition(it) })
}