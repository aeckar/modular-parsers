package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.*
import io.github.aeckar.parsing.utils.Consumer
import io.github.aeckar.parsing.utils.Nameable
import io.github.aeckar.parsing.utils.OnceAssignable
import io.github.aeckar.parsing.utils.unsafeCast
import kotlin.reflect.KProperty

/*
    Junctions and sequences between two characters are not implemented because
    users can simply use a character switch instead.
 */

// ------------------------------ factory functions ------------------------------

/**
 * Creates a new parser.
 * @throws MalformedParserException an implicit, imported, or [start][DefinitionDsl.start] symbol is undefined
 */
public fun parser(definition: NullaryParserDefinitionDsl.() -> Unit): NullaryParser {
    return NullaryParser(NullaryParserDefinitionDsl().apply(definition))
}

/**
 * Creates a new parser that takes an argument.
 *
 * Use of supplied arguments is restricted to the
 * [init][UnaryParserDefinitionDsl.init] block and symbol [listeners][UnaryParserDefinitionDsl.listener].
 * @throws MalformedParserException an implicit, imported, or [start][DefinitionDsl.start] symbol is undefined
 */
public fun <ArgumentT> parser(definition: UnaryParserDefinitionDsl<ArgumentT>.() -> Unit): UnaryParser<ArgumentT> {
    return UnaryParser(UnaryParserDefinitionDsl<ArgumentT>().apply(definition))
}

/**
 * Creates a new parser that tokenizes input.
 * @throws MalformedParserException an implicit, imported, or [start][DefinitionDsl.start] symbol is undefined
 */
public fun lexerParser(definition: NullaryLexerParserDefinitionDsl.() -> Unit): NullaryLexerParser {
    return NullaryLexerParser(NullaryLexerParserDefinitionDsl().apply(definition))
}

/**
 * Creates a new parser that tokenizes input and takes an argument.
 *
 * Use of supplied arguments is restricted to the
 * [init][UnaryLexerParserDefinitionDsl.init] block and symbol [listeners][UnaryLexerParserDefinitionDsl.listener].
 * @throws MalformedParserException an implicit, imported, or [start][DefinitionDsl.start] symbol is undefined
 */
public fun <ArgumentT> lexerParser(
    definition: UnaryLexerParserDefinitionDsl<ArgumentT>.() -> Unit
): UnaryLexerParser<ArgumentT> {
    return UnaryLexerParser(UnaryLexerParserDefinitionDsl<ArgumentT>().apply(definition))
}

// ------------------------------ abstract definition DSLs ------------------------------

/**
 * Defines a scope where [Parser] rules and listeners can be defined.
 *
 * Each listener is invoked from a completed [abstract syntax tree][Parser.toAST] in a top-down, left-to-right fashion.
 */
public sealed class DefinitionDsl {
    /**
     * The principal symbol to be matched.
     */
    public var start: Symbol by OnceAssignable(throws = ::MalformedParserException)

    internal val symbols = mutableMapOf<String, NameableSymbol<*>>()
    internal val implicitSymbols = mutableMapOf<String, NameableSymbol<*>?>()

    protected fun raiseAmbiguousListener(name: String): Nothing {
        throw MalformedParserException("Listener for symbol '$name' defined more than once'")
    }

    /**
     * A view of a single character or switch escape in [string].
     *
     * Enables idiomatic parsing of strings containing values.
     * Can be modified so that this view refers to a different character, or none at all.
     */
    private class SwitchStringView(val string: String) {
        private var index = 0

        fun isWithinBounds() = index < string.length
        fun isNotWithinBounds() = index >= string.length

        inline infix fun satisfies(predicate: (Char) -> Boolean) = isWithinBounds() && predicate(string[index])

        fun char(): Char {
            fun raiseMalformedEscape(): Nothing {
                throw IllegalArgumentException("Malformed switch escape in \"$string\" (index = $index)")
            }

            if (string[index] != '/') {
                return string[index]
            }
            move(1)
            if (isNotWithinBounds()) {
                raiseMalformedEscape()
            }
            return when (char()) {
                '/', '-' -> char()
                else -> raiseMalformedEscape()
            }
        }

        fun move(indexAugment: Int) {
            if (satisfies { it == '/' }) {
                ++index
            }
            index += indexAugment
        }

        override fun toString() = "\"$string\" (index = $index)"
    }

    // ------------------------------ symbol definition and import ------------------------------

    /**
     * Assigns a name to this symbol.
     */
    public operator fun <S : NameableSymbol<S>> NameableSymbol<S>.getValue(
        thisRef: Any?,
        property: KProperty<*>
    ): NamedSymbol<S> {
        val name = property.name
        if (this is TypeUnsafeSymbol<*, *>) {  // Ensure implicit symbol itself is not used for parsing
            implicitSymbols[name] = null
        } else {
            symbols[name] = this
        }
        return NamedSymbol(name, this.unsafeCast())
    }

    /**
     * Allows the importing of a symbol from another named parser.
     */
    public fun <NameableT : NameableSymbol<NameableT>>
    NamedParser.import(): SymbolImport<NameableT> = SymbolImport(this)

    /**
     * Delegating an instance of this class to a property assigns it the
     * [Symbol] of the same name defined in another named parser.
     *
     * If the symbol is not defined in the parser, a [MalformedParserException] will be thrown upon delegation.
     */
    public inner class SymbolImport<NameableT : NameableSymbol<NameableT>> internal constructor(
        private val from: StaticParser
    ) : Nameable {
        override fun getValue(thisRef: Any?, property: KProperty<*>): NamedSymbol<NameableT> {
            val name = property.name
            val symbol = try {
                from.allSymbols.getValue(name)
            } catch (e: NoSuchElementException) {
                throw MalformedParserException("Symbol '$name' is not defined in parser '$from'", e)
            }
            symbols[name] = symbol
            return try {
                NamedSymbol(name, symbol.unsafeCast())
            } catch (e: ClassCastException) {
                throw MalformedParserException(
                    "Symbol '$name' of type ${symbol::class.simpleName} cannot be " +
                    "cast to the type parameter specified by the import statement", e)
            }
        }
    }

    // ------------------------------ implicit symbols ------------------------------

    /**
     * The definition of this implicit symbol.
     *
     * If not assigned at least once, a [MalformedParserException] is thrown after parser initialization.
     * @throws MalformedParserException this property is accessed before it is assigned a value
     */
    public var <TypeSafeT : TypeSafeSymbol<*, *>, TypeUnsafeT : TypeUnsafeSymbol<out TypeSafeT, out TypeUnsafeT>>
    NamedSymbol<out TypeUnsafeT>.actual: TypeSafeT
        get() {
            return try {
                unnamed.unsafeCast()
            } catch (e: ClassCastException) {
                throw MalformedParserException("Definition of '$name' accessed before it was defined", e)
            }
        }
        set(value) {
            symbols[name] = value
            implicitSymbols[name] = value
        }

    /**
     * Returns a [Junction] that can be defined after being delegated to a property.
     *
     * Because the types of the options are erased, they cannot be accessed within listeners.
     */
    public fun junction(): Junction<*> = Junction()

    /**
     * Returns a [Sequence] that can be defined after being delegated to a property.
     *
     * Because the types of the queries are erased, they cannot be accessed within listeners.
     */
    public fun sequence(): Sequence<*> = Sequence()

    // ------------------------------ text & switches ------------------------------

    /**
     * Returns a [Text] symbol.
     */
    protected open fun text(query: String): ParserComponent = Text(query)

    /**
     * Returns a [character switch][Switch] symbol.
     *
     * Should be preferred over a [Junction] of [text symbols][text] each with a single character.
     */
    protected open fun of(switch: String): ParserComponent = with(SwitchStringView(switch)) {
        require(isWithinBounds()) { "Switch definition must not be empty" }
        val ranges = mutableListOf<CharRange>()
        if (char() == '-') {    // Parse "at-most" range
            move(1)
            if (isNotWithinBounds()) {
                return Switch.ANY_CHAR
            }
            ranges += Char.MIN_VALUE..char()
            move(1)
        }
        while (isWithinBounds()) {  // Parse middle ranges and exact characters
            val lowerBound = char()
            move(1)
            if (!satisfies { it == '-' }) {
                ranges += lowerBound..lowerBound
                continue
            }
            move(1)
            if (isNotWithinBounds()) {  // Parse "at-least" range
                ranges += lowerBound..Char.MAX_VALUE
                break
            }
            ranges += lowerBound..char()
            move(1)
        }
        Switch(switch, ranges)
    }

    // ------------------------------ options ------------------------------

    /**
     * Returns an [Option] of the given symbol.
     *
     * For [Text] symbols, consider using the appropriate overload.
     */
    public fun <QueryT : Symbol> maybe(query: QueryT): Option<QueryT> = Option(query)

    // ------------------------------ repetitions ------------------------------

    /**
     * Returns a [Repetition] of the given symbol.
     *
     * For [Text] symbols, consider using the appropriate overload.
     */
    public fun <QueryT : Symbol> multiple(query: QueryT): Repetition<QueryT> = Repetition(query)

    // ------------------------------ optional repetitions ------------------------------

    /**
     * Returns an optional repetition of the given symbol.
     */
    public fun <QueryT : Symbol> any(query: QueryT): Option<Repetition<QueryT>> = maybe(multiple(query))

    // ------------------------------ junctions ------------------------------

    // Allow type-safe junctions and sequences to be root of new junction/sequence (types are checked anyway)

    /**
     * Returns a [Junction] of the two symbols.
     */
    public infix fun <S1 : Symbol, S2 : Symbol> S1.or(option2: S2): Junction2<S1, S2> = toJunction(this, option2)

    protected fun <S1 : Symbol, S2 : Symbol> toJunction(option1: S1, option2: S2): Junction2<S1, S2> {
        return Junction2(Junction(option1, option2).unsafeCast())
    }

    // ------------------------------ sequences ------------------------------

    /**
     * Returns a [Sequence] containing the two symbols.
     */
    public operator fun <S1 : Symbol, S2 : Symbol> S1.plus(query2: S2): Sequence2<S1, S2> = toSequence(this, query2)

    protected fun <S1 : Symbol, S2 : Symbol> toSequence(query1: S1, query2: S2): Sequence2<S1, S2> {
        return Sequence2(Sequence(query1, query2).unsafeCast())
    }
}

/**
 * Defines a scope where a [Parser] without a lexer can be defined.
 */
public sealed class ParserDefinitionDsl : DefinitionDsl() {
    /**
     * The symbol whose matches are discarded during parsing.
     *
     * Whenever a successful match is made, whatever is then matched to this symbol is ignored.
     */
    public var skip: Symbol by OnceAssignable(throws = ::MalformedParserException)

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
 * Defines a scope where a [lexer-parser][LexerParser] can be defined.
 */
public sealed class LexerParserDefinitionDsl : DefinitionDsl() {
    /**
     * The lexer symbols to be ignored during lexical analysis.
     *
     * Nodes produced by these symbols will not be present in the list returned by [LexerParser.tokenize].
     */
    public val skip: MutableList<NamedSymbol<LexerSymbol>> = mutableListOf()    // TODO copy to immutable later

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

// ------------------------------ concrete definition DSLs ------------------------------

/**
 * A [definition][DefinitionDsl] of a parser without an argument.
 */
public sealed interface NullaryDefinitionDsl {
    /**
     * Assigns the supplied listener to the symbol.
     *
     * Whenever a match is made to this symbol, the listener is invoked.
     */
    public infix fun <MatchT : NameableSymbol<MatchT>> NamedSymbol<MatchT>.listener(action: NullaryListener<MatchT>)
}

/**
 * A [definition][DefinitionDsl] of a parser with one argument.
 */
public sealed interface UnaryDefinitionDsl<ArgumentT> {
    /**
     * Assigns the supplied listener to the symbol.
     *
     * Whenever a match is made to this symbol, the listener is invoked.
     */
    public infix fun <MatchT : NameableSymbol<MatchT>> NamedSymbol<MatchT>.listener(
        action: UnaryListener<MatchT, ArgumentT>
    )

    /**
     * Describes the initialization logic of the argument supplied to this parser.
     */
    public fun init(initializer: Consumer<ArgumentT>)
}

/**
 * Defines a scope where a [Parser] without a lexer that does not take an argument can be defined.
 */
public class NullaryParserDefinitionDsl internal constructor() : ParserDefinitionDsl(), NullaryDefinitionDsl {
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
public class UnaryParserDefinitionDsl<ArgumentT>
internal constructor() : ParserDefinitionDsl(), UnaryDefinitionDsl<ArgumentT> {
    internal val listeners = mutableMapOf<String, UnaryListener<*, ArgumentT>>()

    internal var initializer: Consumer<ArgumentT>? = null

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
    override fun init(initializer: Consumer<ArgumentT>) {
        this.initializer?.let { throw MalformedParserException("Initializer defined more than once") }
        this.initializer = initializer
    }
}

/**
 * Defines a scope where a [LexerParser] that does not take an argument can be defined.
 */
public class NullaryLexerParserDefinitionDsl internal constructor(
    internal val base: NullaryParserDefinitionDsl = NullaryParserDefinitionDsl()    // Never explicitly given
) : LexerParserDefinitionDsl(), NullaryDefinitionDsl by base

/**
 * Defines a scope where a [LexerParser] that takes one argument can be defined.
 */
public class UnaryLexerParserDefinitionDsl<ArgumentT> internal constructor(
    internal val base: UnaryParserDefinitionDsl<ArgumentT> = UnaryParserDefinitionDsl() // Never explicitly given
) : LexerParserDefinitionDsl(), UnaryDefinitionDsl<ArgumentT> by base