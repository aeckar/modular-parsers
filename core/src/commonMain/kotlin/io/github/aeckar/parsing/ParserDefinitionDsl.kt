package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.Junction2
import io.github.aeckar.parsing.typesafe.Sequence2
import io.github.aeckar.parsing.utils.unsafeCast
import kotlin.reflect.KProperty

/**
 * Creates a new parser that takes no arguments.
 *
 * The symbols in the returned parser are resolved statically during initialization.
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinitionDsl.start] symbol is undefined
 */
public fun nullaryParser(definition: ParserDefinitionDsl.() -> Unit): NullaryParser {
    return NullaryParser(NullaryParserDefinitionDsl().apply(definition))
}

/**
 * Creates a new parser that takes an argument.
 *
 * The symbols in the returned parser are resolved statically during initialization.
 *
 * Use of supplied arguments is restricted to the
 * [init][UnaryParserDefinitionDsl.init] block and symbol [listeners][UnaryParserDefinitionDsl.listener].
 * @throws MalformedParserException an implicit, imported, or [start][ParserDefinitionDsl.start] symbol is undefined
 */
public fun <A> unaryParser(definition: UnaryParserDefinitionDsl<A>.() -> Unit): UnaryParser<A> {
    return UnaryParser(UnaryParserDefinitionDsl<A>().apply(definition))
}

/**
 * Creates a new, dynamic parser that takes an argument.
 *
 * The symbols in the returned parser are resolved dynamically at the time of parsing.
 *
 * Supplied arguments can be used anywhere inside the definition of this parser.
 * Any use of them outside [listeners][UnaryParserDefinitionDsl.listener] constitutes their initialization logic.
 *
 * If at the time of parsing an implicit, imported, or [start][ParserDefinitionDsl.start] symbol is undefined,
 * a [MalformedParserException] will be thrown.
 */
public fun <A> dynamicParser(definition: ParserDefinitionDsl.(A) -> Unit) : DynamicParser<A> {
    return DynamicParser<A>(definition)
}

/**
 * Defines a scope where parser rules and listeners can be defined.
 *
 * Each listener is invoked from a completed [abstract syntax tree][NamedNullaryParser.accept]
 * in a top-down, left-to-right fashion.
 */
public sealed class ParserDefinitionDsl {
    /**
     * The principal symbol to be matched.
     */
    public lateinit var start: Symbol

    /**
     * The symbol whose matches are discarded during parsing.
     */
    public lateinit var skip: Symbol

    internal val symbols = mutableMapOf<String, NameableSymbol<*>>()
    internal val implicitSymbols = mutableMapOf<String, NameableSymbol<*>?>()

    internal fun skipOrNull() = if (::skip.isInitialized) skip else null

    protected fun raiseAmbiguousListener(name: String) {
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
    public operator fun <S : Symbol> NameableSymbol<S>.getValue(thisRef: Any?, property: KProperty<*>): NamedSymbol<S> {
        val name = property.name
        symbols[name] = this
        return NamedSymbol(name, this.unsafeCast())
    }

    /**
     * Allows the importing of a symbol from another, named parser.
     */
    public fun <S : NameableSymbol<*>> StaticParser.import(): SymbolImport<S> = SymbolImport(this)

    /**
     * Delegating an instance of this class to a property assigns it the
     * [symbol][Symbol] of the same name defined in another named, static parser.
     *
     * If the symbol is not defined in the parser, a [MalformedParserException] will be thrown upon delegation.
     */
    public inner class SymbolImport<S : Symbol> internal constructor(
        private val from: StaticParser
    ) : Nameable {
        override fun getValue(thisRef: Any?, property: KProperty<*>): NamedSymbol<S> {
            val name = property.name
            val symbol = try {
                from.allSymbols.getValue(name)
            } catch (e: NoSuchElementException) {
                throw MalformedParserException("Symbol '$name' is not defined in parser '$from'", e)
            }
            symbols[name] = symbol
            return try {
                NamedSymbol(name, symbol).unsafeCast()
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
     */
    public var <S : ComplexSymbol<S>> NamedSymbol<S>.actual: S
        get() = unnamed
        set(value) {
            symbols[name] = value
        }

    /**
     * Returns a [junction][Junction] that can be defined after being delegated to a property.
     *
     * Because the types of the options cannot be resolved upon naming, they are not cast within listeners.
     */
    public fun junction(): Junction = Junction()

    /**
     * Returns a [sequence][Sequence] that can be defined after being delegated to a property.
     *
     * Because the types of the queries cannot be resolved upon naming, they are not cast within listeners.
     */
    public fun sequence(): Sequence = Sequence()

    // ------------------------------ literals ------------------------------

    public operator fun Char.getValue(context: Nothing?, symbol: KProperty<*>): NamedSymbol<Text> {
        return NamedSymbol(symbol.name, Text(this))
    }

    /**
     * Returns a [text][Text] symbol.
     */
    public fun text(query: String): Text = Text(query)

    /**
     * Returns a [character switch][Switch] symbol.
     *
     * Should be preferred over a [junction] of [text symbols][text] each with a single character.
     */
    public fun of(switch: String): Switch = with(SwitchStringView(switch)) {
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
     * Returns an [option][Option] of the given symbol.
     *
     * For [text][Text] symbols, consider using the appropriate overload.
     */
    public fun <S : Symbol> maybe(query: S): Option<S> = Option(query)

    /**
     * Returns a text [option][Option].
     */
    public fun maybe(query: String): Option<Text> = Option(Text(query))

    /**
     * Returns a text [option][Option].
     */
    public fun maybe(query: Char): Option<Text> = Option(Text(query))

    /**
     * Returns a switch [option][Option].
     */
    public fun maybeOf(switch: String): Option<Switch> = maybe(of(switch))

    // ------------------------------ repetitions ------------------------------

    /**
     * Returns a [repetition][Repetition] of the given symbol.
     *
     * For [text][Text] symbols, consider using the appropriate overload.
     */
    public fun <S : Symbol> multiple(query: S): Repetition<S> = Repetition(query)

    /**
     * Returns a text [repetition][Repetition].
     */
    public fun multiple(query: String): Repetition<Text> = Repetition(Text(query))

    /**
     * Returns a text [repetition][Repetition].
     */
    public fun multiple(query: Char): Option<Text> = Option(Text(query))

    /**
     * Returns a switch [repetition][Repetition].
     */
    public fun multipleOf(switch: String): Repetition<Switch> = multiple(of(switch))

    // ------------------------------ optional repetitions ------------------------------

    /**
     * Returns an optional repetition of the given symbol.
     */
    public fun <S : Symbol> any(query: S): Option<Repetition<S>> = maybe(multiple(query))

    /**
     * Returns an optional repetition of the text.
     */
    public fun any(query: String): Option<Repetition<Text>> = maybe(multiple(Text(query)))

    /**
     * Returns an optional repetition of the text.
     */
    public fun any(query: Char): Option<Repetition<Text>> = maybe(multiple(Text(query)))

    /**
     * Returns an optional repetition of the text.
     */
    public fun anyOf(switch: String): Option<Repetition<Switch>> = any(of(switch))

    // ------------------------------ junctions ------------------------------

    /**
     * Returns a junction of the two symbols.
     */
    public infix fun <S1 : Symbol, S2 : Symbol> S1.or(option2: S2): Junction2<S1, S2> = toJunction(this, option2)

    /**
     * Returns a junction of this text and the given symbol.
     */
    public infix fun <S2 : Symbol> Char.or(option2: S2): Junction2<Text, S2> = toJunction(Text(this), option2)

    /**
     * Returns a junction of this symbol and the given text.
     */
    public infix fun <S1 : Symbol> S1.or(option2: Char): Junction2<S1, Text> = toJunction(this, Text(option2))

    private fun <S1 : Symbol, S2 : Symbol> toJunction(option1: S1, option2: S2): Junction2<S1, S2> {
        val junction = Junction().apply {
            components += option1
            components += option2
        }
        return Junction2(junction)
    }

    // ------------------------------ sequences ------------------------------

    /**
     * Returns a sequence containing the two symbols.
     */
    public operator fun <S1 : Symbol, S2 : Symbol> S1.plus(query2: S2): Sequence2<S1, S2> = toSequence(this, query2)

    /**
     * Returns a sequence containing this text and the given symbol.
     */
    public operator fun <S2 : Symbol> Char.plus(query2: S2): Sequence2<Text, S2> = toSequence(Text(this), query2)

    /**
     * Returns a sequence containing this symbol and the given text.
     */
    public operator fun <S1 : Symbol> S1.plus(query2: Char): Sequence2<S1, Text> = toSequence(this, Text(query2))

    private fun <S1 : Symbol, S2 : Symbol> toSequence(query1: S1, query2: S2): Sequence2<S1, S2> {
        val sequence = Sequence().apply {
            components += query1
            components += query2
        }
        return Sequence2(sequence)
    }
}

public class NullaryParserDefinitionDsl internal constructor() : ParserDefinitionDsl() {
    internal val listeners = mutableMapOf<String, Listener<*>>()

    /**
     * Assigns an action to be performed whenever a successful match is made using this symbol.
     */
    public infix fun <T : Symbol> NamedSymbol<T>.listener(action: Listener<T>) {
        if (name in listeners) {
            raiseAmbiguousListener(name)
        }
        listeners[name] = action
    }
}

public open class ParserDefinitionWithArgumentDsl<A> internal constructor() : ParserDefinitionDsl() {
    internal val listeners = mutableMapOf<String, ListenerWithArgument<*, A>>()

    /**
     * Assigns an action to be performed whenever a successful match is made using this symbol.
     */
    public infix fun <T : Symbol> NamedSymbol<T>.listener(action: ListenerWithArgument<T, A>) {
        if (name in listeners) {
            raiseAmbiguousListener(name)
        }
        listeners[name] = action
    }
}
public open class UnaryParserDefinitionDsl<A> internal constructor() : ParserDefinitionWithArgumentDsl<A>() {
    internal var initializer: Consumer<A>? = null

    /**
     * Describes the initialization logic of arguments supplied to this parser.
     */
    public fun init(initializer: Consumer<A>) {
        this.initializer?.let { throw MalformedParserException("Initializer defined more than once") }
        this.initializer = initializer
    }
}