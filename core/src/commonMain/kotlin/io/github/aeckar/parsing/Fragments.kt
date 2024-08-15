package io.github.aeckar.parsing

/**
 * Can be combined with other fragments to create a [LexerSymbol].
 */
public class SymbolFragment internal constructor(
    internal val root: Symbol
) : ParserComponent {
    internal constructor(root: BasicSymbol<*>) : this(root as Symbol)

    /**
     * The name assigned to this symbol if it exists, else its EBNF representation.
     */
    internal val rawName = root.rawName

    internal fun lex(data: ParserMetadata) = root.match(data)?.substring
}