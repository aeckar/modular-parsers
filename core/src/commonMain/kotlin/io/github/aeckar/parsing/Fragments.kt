package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.TypeSafeJunction
import io.github.aeckar.parsing.typesafe.TypeSafeSequence

/**
 * Can be combined with other fragments to create a [lexer symbol][LexerSymbol].
 */
public open class /* (or interface) */ Fragment protected constructor(
    internal val root: Symbol
) : ParserComponent {
    internal constructor(root: SimpleSymbol<*>) : this(root as Symbol)

    /**
     * The name assigned to this symbol if it exists, else its EBNF representation.
     */
    internal val rawName = root.rawName

    internal open fun match(data: ParserMetadata) = root.match(data)?.substring
}

internal class JunctionFragment(root: TypeSafeJunction<*>) : Fragment(root) {
    override fun match(data: ParserMetadata): String? {

    }
}

internal class SequenceFragment(root: TypeSafeSequence<*>) : Fragment(root) {
    override fun match(data: ParserMetadata): String? {

    }
}