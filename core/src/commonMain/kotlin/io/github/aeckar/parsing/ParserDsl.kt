package io.github.aeckar.parsing

import io.github.aeckar.parsing.Listener
import kotlin.reflect.KProperty

/**
 * @throws MalformedParserException todo
 */
public fun parser(definition: ParserDsl.() -> Unit): Parser = Parser(definition)

/**
 * Defines a scope where parser rules and listeners can be defined.
 *
 * Each listener is invoked from a completed [abstract syntax tree][todo] in a top-down, left-to-right fashion.
 */
public open class ParserDsl internal constructor() {    // todo Create subclass for parsers accumulating errors
    /**
     * The principal symbol to be matched.
     */
    public lateinit var start: Symbol

    /**
     * The symbol whose matches are discarded during parsing.
     */
    public lateinit var skip: Symbol

    // TODO check that all symbols have been defined before parser exiting parser()
    private val definitions = mutableMapOf<String, Symbol?>()
    private val listeners = mutableMapOf<String, Listener<*>>()

    // ------------------------------ implicit symbols ------------------------------

    /**
     * The definition of this implicit symbol.
     *
     * If not assigned at least once, a [MalformedParserException] is thrown after parser initialization.
     */
    public var <S : ComplexSymbol<S>> NamedSymbol<S>.actual: S
        get() = named
        set(value) {
            definitions[name] = value
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

    // ...remaining junction functions generated by Gradle

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

    // ...remaining sequence functions generated by Gradle

    // ------------------------------ listener DSL ------------------------------

    private fun <T : Symbol> defineListener(name: String, action: Listener<T>) {
        if (name in listeners) {
            throw MalformedParserException("Listener for symbol '$name' defined more than once'")
        }
        listeners[name] = action.unsafeCast()
    }

    /**
     * Assigns an action to be performed whenever a successful match is made using this symbol.
     *
     * For named symbols that do not represent a [text][Text] nor [switch][Switch],
     * they can be explicitly cast to `NamedSymbol<*>` to ensure that the listener does not access
     * the individual matches produced by the symbol.
     *
     * TODO example here
     */
    public infix fun <S : NamedSymbol<T>, T : Symbol> S.listener(action: Listener<T>) {
        defineListener(name, action)
    }

    public infix fun <S : NamedSymbol<T>, T : Option<*>> S.listener(action: Listener<T>) {
        defineListener(name, action)
    }

    public infix fun <S : NamedSymbol<T>, T : Repetition<*>> S.listener(action: Listener<T>) {
        defineListener(name, action)
    }

    public infix fun <S : NamedSymbol<T>, T : JunctionN<*>> S.listener(action: Listener<T>) {
        defineListener(name, action)
    }
}