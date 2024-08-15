package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.*
import io.github.aeckar.parsing.utils.Nameable
import io.github.aeckar.parsing.utils.OnceAssignable
import io.github.aeckar.parsing.utils.unsafeCast
import kotlin.reflect.KProperty

/*
    Junctions and sequences between two characters are not implemented because
    users can simply use a character switch instead.
 */

internal val ParserDefinition.listeners get() = when (this) {
    is NullaryLexerlessParserDefinition -> listeners
    is UnaryLexerlessParserDefinition<*> -> listeners
    is NullaryLexerParserDefinition -> base.listeners
    is UnaryLexerParserDefinition<*> -> base.listeners
}

/**
 * Defines a scope where [Parser] rules and listeners can be defined.
 *
 * Each listener is invoked from a completed [abstract syntax tree][Parser.parse] in a top-down, left-to-right fashion.
 */
public sealed class ParserDefinition {
    /**
     * The principal symbol to be matched.
     */
    public var start: Symbol by OnceAssignable(throws = ::MalformedParserException)

    internal val parserSymbols = mutableMapOf<String, NameableSymbol<*>>()
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
            parserSymbols[name] = this
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
        private val from: NamedParser
    ) : Nameable {
        override fun getValue(thisRef: Any?, property: KProperty<*>): NamedSymbol<NameableT> {
            val name = property.name
            val symbol = try {
                from.parserSymbols.getValue(name)
            } catch (e: NoSuchElementException) {
                throw MalformedParserException("Symbol '$name' is not defined in parser '$from'", e)
            }
            parserSymbols[name] = symbol
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
            parserSymbols[name] = value
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
 * A [definition][ParserDefinition] of a parser without an argument.
 */
public sealed interface NullaryParserDefinition {
    /**
     * Assigns the supplied listener to the symbol.
     *
     * Whenever a match is made to this symbol, the listener is invoked.
     */
    public infix fun <MatchT : NameableSymbol<MatchT>> NamedSymbol<MatchT>.listener(action: NullaryListener<MatchT>)
}

/**
 * A [definition][ParserDefinition] of a parser with one argument.
 */
public sealed interface UnaryParserDefinition<ArgumentT> {
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
    public fun init(initializer: (ArgumentT) -> Unit)
}