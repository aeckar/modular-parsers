package io.github.aeckar.parsing

import io.github.aeckar.parsing.utils.OnceAssignable
import io.github.aeckar.parsing.utils.unsafeCast
import kotlin.reflect.KProperty

/**
 * Defines a scope where a [LexerParser] can be defined.
 */
public sealed class LexerParserDefinition : ParserDefinition() {
    internal val recoveryDelegate = OnceAssignable<Symbol, _>(::MalformedParserException)

    /**
     * During [tokenization][Lexer.tokenize], if a sequence of characters cannot be matched to a named [LexerSymbol],
     * any adjacent substrings matching this symbol that do not match a named lexer symbol
     * are combined into a single unnamed token.
     *
     * If left unspecified and a match cannot be made to a named lexer symbol, an [IllegalTokenException] is thrown.
     * This exception is also thrown if a match to this symbol produces a token of an empty substring
     * (e.g., if this is an [Option]).
     * @throws MalformedParserException this property is left unassigned, or is assigned a value more than once
     */
    public val recovery: Symbol by recoveryDelegate

    /**
     * The lexer symbols to be ignored during lexical analysis.
     *
     * Nodes produced by these symbols will not be present in the list returned by [LexerParser.tokenize].
     * @throws MalformedParserException this property is left unassigned, or is assigned a value more than once
     */
    public val skip: MutableList<NamedSymbol<LexerSymbol>> = mutableListOf()


    internal val lexerSymbols = mutableListOf<NamedSymbol<LexerSymbol>>()

    // ------------------------------ symbol definition ------------------------------

    /**
     * Assigns a [LexerSymbol] matching this fragment to the property being delegated to.
     */
    public operator fun SymbolFragment.getValue(thisRef: Any?, symbol: KProperty<*>): NamedSymbol<LexerSymbol> {
        return NamedSymbol(symbol.name, LexerSymbol(this))
    }

    // ------------------------------ text & switches ------------------------------

    /**
     * Assigns a [LexerSymbol] matching this character to the property being delegated to.
     */
    public operator fun Char.getValue(thisRef: Any?, symbol: KProperty<*>): NamedSymbol<LexerSymbol> {
        return NamedSymbol(symbol.name, LexerSymbol(SymbolFragment(Text(this)))).also { lexerSymbols += it }
    }

    /**
     * Returns a [Text] fragment.
     */
    public final override fun text(query: String): SymbolFragment = SymbolFragment(Text(query))

    /**
     * Returns a [character switch][Switch] fragment.
     *
     * Should be preferred over a [Junction][or] of [Text] fragments each with a single character.
     */
    public final override fun of(switch: String): SymbolFragment = SymbolFragment(super.of(switch).unsafeCast())

    // ------------------------------ options ------------------------------

    /**
     * Return an [Option] of the given fragment.
     */
    public fun maybe(query: SymbolFragment): SymbolFragment = SymbolFragment(maybe(query.root))

    /**
     * Returns a text [Option].
     */
    public fun maybe(query: String): SymbolFragment = SymbolFragment(Option(Text(query)))

    /**
     * Returns a text [Option].
     */
    public fun maybe(query: Char): SymbolFragment = SymbolFragment(Option(Text(query)))

    /**
     * Returns a switch [Option].
     */
    public fun maybeOf(switch: String): SymbolFragment = SymbolFragment(maybe(super.of(switch) as Switch))

    // ------------------------------ repetitions ------------------------------

    /**
     * Returns a [Repetition] of the given fragment.
     */
    public fun multiple(query: SymbolFragment): SymbolFragment = SymbolFragment(multiple(query.root))

    /**
     * Returns a text [Repetition].
     */
    public fun multiple(query: String): SymbolFragment = SymbolFragment(Repetition(Text(query)))

    /**
     * Returns a text [Repetition].
     */
    public fun multiple(query: Char): SymbolFragment = SymbolFragment(Option(Text(query)))

    /**
     * Returns a switch [Repetition].
     */
    public fun multipleOf(switch: String): SymbolFragment = SymbolFragment(multiple(super.of(switch) as Switch))

    // ------------------------------ optional repetitions ------------------------------

    /**
     * Returns an optional repetition of the given fragment.
     */
    public fun any(query: SymbolFragment): SymbolFragment = SymbolFragment(maybe(multiple(query.root)))

    /**
     * Returns an optional repetition of the text.
     */
    public fun any(query: String): SymbolFragment = SymbolFragment(maybe(multiple(Text(query))))

    /**
     * Returns an optional repetition of the text.
     */
    public fun any(query: Char): SymbolFragment = SymbolFragment(maybe(multiple(Text(query))))

    /**
     * Returns an optional repetition of the switch.
     */
    public fun anyOf(switch: String): SymbolFragment = SymbolFragment(any(super.of(switch) as Switch))

    // ------------------------------ junctions ------------------------------

    /**
     * Returns a [Junction] of the two fragments.
     */
    public infix fun SymbolFragment.or(option2: SymbolFragment): SymbolFragment {
        val other = option2.root
        if (root is Junction<*>) {
            root.components += other
            return this
        }
        if (other is Junction<*>) {
            other.components += root
            return option2
        }
        return SymbolFragment(Junction(root, other))
    }

    /**
     * Returns a [Junction] of this text and the given fragment.
     */
    public infix fun Char.or(option2: SymbolFragment): SymbolFragment {
        val other = option2.root
        if (other is Junction<*>) {
            other.components += Text(this)
            return option2
        }
        return SymbolFragment(Junction(Text(this), other))
    }

    /**
     * Returns a [Junction] of this fragment and the given text.
     */
    public infix fun SymbolFragment.or(option2: Char): SymbolFragment {
        if (root is Junction<*>) {
            root.components += Text(option2)
            return this
        }
        return SymbolFragment(Junction(Text(option2), root))
    }

    // ------------------------------ sequences ------------------------------

    /**
     * Returns a [Sequence] containing the two fragments
     */
    public operator fun SymbolFragment.plus(query2: SymbolFragment): SymbolFragment {
        val other = query2.root
        if (root is Sequence<*>) {
            root.components += other
            return this
        }
        if (other is Sequence<*>) {
            other.components += root
            return query2
        }
        return SymbolFragment(Sequence(root, other))
    }

    /**
     * Returns a [Sequence] containing this text and the given fragment.
     */
    public operator fun Char.plus(query2: SymbolFragment): SymbolFragment {
        val other = query2.root
        if (other is Sequence<*>) {
            other.components += Text(this)
            return query2
        }
        return SymbolFragment(Sequence(Text(this), other))
    }

    /**
     * Returns a [Sequence] containing this fragment and the given text.
     */
    public operator fun SymbolFragment.plus(query2: Char): SymbolFragment {
        if (root is Sequence<*>) {
            root.components += Text(query2)
            return this
        }
        return SymbolFragment(Sequence(Text(query2), root))
    }
}

/**
 * Defines a scope where a [LexerParser] that does not take an argument can be defined.
 */
public class NullaryLexerParserDefinition internal constructor(  // Argument never explicitly given
    internal val base: NullaryLexerlessParserDefinition = NullaryLexerlessParserDefinition()
) : LexerParserDefinition(), NullaryParserDefinition by base

/**
 * Defines a scope where a [LexerParser] that takes one argument can be defined.
 */
public class UnaryLexerParserDefinition<ArgumentT> internal constructor( // Argument never explicitly given
    internal val base: UnaryLexerlessParserDefinition<ArgumentT> = UnaryLexerlessParserDefinition()
) : LexerParserDefinition(), UnaryParserDefinition<ArgumentT> by base