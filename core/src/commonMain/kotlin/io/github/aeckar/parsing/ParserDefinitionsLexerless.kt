package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.Junction2
import io.github.aeckar.parsing.typesafe.Sequence2
import io.github.aeckar.parsing.utils.OnceAssignable
import io.github.aeckar.parsing.utils.unsafeCast
import kotlin.reflect.KProperty

/**
 * Defines a scope where a [Parser] without a lexer can be defined.
 */
public sealed class LexerlessParserDefinition : ParserDefinition() {
    internal var skipDelegate = OnceAssignable<Symbol, _>(throws = ::MalformedParserException)

    /**
     * The symbol whose matches are discarded during parsing.
     *
     * Whenever a successful match is made, whatever is then matched to this symbol is ignored.
     */
    public var skip: Symbol by skipDelegate

    // ------------------------------ text & switches ------------------------------

    /**
     * Assigns a [Text] symbol of the single character to the property being delegated to.
     */
    public operator fun Char.getValue(thisRef: Any?, symbol: KProperty<*>): NamedSymbol<Text> {
        return NamedSymbol(symbol.name, Text(this))
    }

    /**
     * Returns a [Text] symbol.
     */
    public final override fun text(query: String): Text = super.text(query).unsafeCast()

    /**
     * Returns a [character switch][Switch] symbol.
     *
     * Should be preferred over a [Junction][or] of [text symbols][text] each with a single character.
     */
    public final override fun of(switch: String): Switch = super.of(switch).unsafeCast()

    // ------------------------------ options ------------------------------

    /**
     * Returns a text [Option].
     */
    public fun maybe(query: String): Option<Text> = Option(Text(query))

    /**
     * Returns a text [Option].
     */
    public fun maybe(query: Char): Option<Text> = Option(Text(query))

    /**
     * Returns a switch [Option].
     */
    public fun maybeOf(switch: String): Option<Switch> = maybe(of(switch))

    // ------------------------------ repetitions ------------------------------

    /**
     * Returns a text [Repetition].
     */
    public fun multiple(query: String): Repetition<Text> = Repetition(Text(query))

    /**
     * Returns a text [Repetition].
     */
    public fun multiple(query: Char): Option<Text> = Option(Text(query))

    /**
     * Returns a switch [Repetition].
     */
    public fun multipleOf(switch: String): Repetition<Switch> = multiple(of(switch))

    // ------------------------------ optional repetitions ------------------------------

    /**
     * Returns an optional repetition of the text.
     */
    public fun any(query: String): Option<Repetition<Text>> = maybe(multiple(Text(query)))

    /**
     * Returns an optional repetition of the text.
     */
    public fun any(query: Char): Option<Repetition<Text>> = maybe(multiple(Text(query)))

    /**
     * Returns an optional repetition of the character switch.
     */
    public fun anyOf(switch: String): Option<Repetition<Switch>> = any(of(switch))

    // ------------------------------ junctions ------------------------------

    /**
     * Returns a [Junction] of this text and the given symbol.
     */
    public infix fun <S2 : Symbol> Char.or(option2: S2): Junction2<Text, S2> = toJunction(Text(this), option2)

    /**
     * Returns a [Junction] of this symbol and the given text.
     */
    public infix fun <S1 : Symbol> S1.or(option2: Char): Junction2<S1, Text> = toJunction(this, Text(option2))

    // ------------------------------ sequences ------------------------------

    /**
     * Returns a [Sequence] containing this text and the given symbol.
     */
    public operator fun <S2 : Symbol> Char.plus(query2: S2): Sequence2<Text, S2> = toSequence(Text(this), query2)

    /**
     * Returns a [Sequence] containing this symbol and the given text.
     */
    public operator fun <S1 : Symbol> S1.plus(query2: Char): Sequence2<S1, Text> = toSequence(this, Text(query2))
}

/**
 * Defines a scope where a [Parser] without a lexer that does not take an argument can be defined.
 */
public class NullaryLexerlessParserDefinition internal constructor(
) : LexerlessParserDefinition(), NullaryParserDefinition {
    internal val listeners = mutableMapOf<String, NullaryListener<*>>()

    /**
     * Assigns an action to be performed whenever a successful match is made using this symbol.
     */
    override fun <MatchT : NameableSymbol<MatchT>> NamedSymbol<MatchT>.listener(action: NullaryListener<MatchT>) {
        if (name in listeners) {
            raiseAmbiguousListener(name)
        }
        listeners[name] = action
    }
}

/**
 * Defines a scope where a [Parser] without a lexer that takes one argument can be defined.
 */
public class UnaryLexerlessParserDefinition<ArgumentT> internal constructor(
) : LexerlessParserDefinition(), UnaryParserDefinition<ArgumentT> {
    internal val listeners = mutableMapOf<String, UnaryListener<*, ArgumentT>>()

    internal var initializer: ((ArgumentT) -> Unit)? = null

    /**
     * Assigns an action to be performed whenever a successful match is made using this symbol.
     */
    override fun <MatchT : NameableSymbol<MatchT>> NamedSymbol<MatchT>.listener(
        action: UnaryListener<MatchT, ArgumentT>
    ) {
        if (name in listeners) {
            raiseAmbiguousListener(name)
        }
        listeners[name] = action
    }
    /**
     * Describes the initialization logic of arguments supplied to this parser.
     */
    override fun init(initializer: (ArgumentT) -> Unit) {
        this.initializer?.let { throw MalformedParserException("Initializer defined more than once") }
        this.initializer = initializer
    }
}