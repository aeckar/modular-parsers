package io.github.aeckar.parsing

/**
 * Creates a new [LexerlessParser].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
public fun parser(definition: NullaryLexerlessParserDefinition.() -> Unit): NullaryLexerlessParser {
    return NullaryLexerlessParser(NullaryLexerlessParserDefinition().apply(definition))
}

/**
 * Creates a new [LexerlessParser] that takes an argument.
 *
 * Use of supplied arguments is restricted to the
 * [init][UnaryLexerlessParserDefinition.init] block and symbol [listeners][UnaryLexerlessParserDefinition.listener].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
public fun <ArgumentT> parser(
    definition: UnaryLexerlessParserDefinition<ArgumentT>.() -> Unit
): UnaryLexerlessParser<ArgumentT> {
    return UnaryLexerlessParser(UnaryLexerlessParserDefinition<ArgumentT>().apply(definition))
}

/**
 * Creates a new [LexerParser].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
public fun lexerParser(definition: NullaryLexerParserDefinition.() -> Unit): NullaryLexerParser {
    return NullaryLexerParser(NullaryLexerParserDefinition().apply(definition))
}

/**
 * Creates a new [LexerParser] that takes an argument.
 *
 * Use of supplied arguments is restricted to the
 * [init][UnaryLexerParserDefinition.init] block and symbol [listeners][UnaryLexerParserDefinition.listener].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinition.start] symbol is undefined
 */
public fun <ArgumentT> lexerParser(
    definition: UnaryLexerParserDefinition<ArgumentT>.() -> Unit
): UnaryLexerParser<ArgumentT> {
    return UnaryLexerParser(UnaryLexerParserDefinition<ArgumentT>().apply(definition))
}