package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.*
import io.github.aeckar.parsing.utils.Consumer
import io.github.aeckar.parsing.utils.Nameable
import io.github.aeckar.parsing.utils.OnceAssignable
import io.github.aeckar.parsing.utils.unsafeCast
import kotlin.reflect.KProperty

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
public fun <A> parser(definition: UnaryParserDefinitionDsl<A>.() -> Unit): UnaryParser<A> {
    return UnaryParser(UnaryParserDefinitionDsl<A>().apply(definition))
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
public fun <A> lexerParser(definition: UnaryLexerParserDefinitionDsl<A>.() -> Unit): UnaryLexerParser<A> {
    return UnaryLexerParser(UnaryLexerParserDefinitionDsl<A>().apply(definition))
}

/**
 * Defines a scope where [parser][Parser] rules and listeners can be defined.
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
        if (this is ComplexSymbol<*, *>) {  // Ensure implicit symbol itself is not used for parsing
            implicitSymbols[name] = null
        } else {
            symbols[name] = this
        }
        return NamedSymbol(name, this.unsafeCast())
    }

    /**
     * Allows the importing of a symbol from another named parser.
     */
    public fun <S : NameableSymbol<S>> NamedParser.import(): SymbolImport<S> = SymbolImport(this)

    // TODO import token from lexer-parser

    /**
     * Delegating an instance of this class to a property assigns it the
     * [symbol][Symbol] of the same name defined in another named parser.
     *
     * If the symbol is not defined in the parser, a [MalformedParserException] will be thrown upon delegation.
     */
    public inner class SymbolImport<S : NameableSymbol<S>> internal constructor(
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
    public var <U : TypeSafeSymbol<*, *>, S : ComplexSymbol<out U, out S>> NamedSymbol<out S>.actual: U
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
     * Returns a [junction][Junction] that can be defined after being delegated to a property.
     *
     * Because the types of the options are erased, they cannot be accessed within listeners.
     */
    public fun junction(): Junction<*> = Junction()

    /**
     * Returns a [sequence][Sequence] that can be defined after being delegated to a property.
     *
     * Because the types of the queries are erased, they cannot be accessed within listeners.
     */
    public fun sequence(): Sequence<*> = Sequence()

    // ------------------------------ text & switches ------------------------------

    /**
     * Returns a [text][Text] symbol.
     */
    protected open fun text(query: String): ParserComponent = Text(query)

    /**
     * Returns a [character switch][Switch] symbol.
     *
     * Should be preferred over a [junction] of [text symbols][text] each with a single character.
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
     * Returns an [option][Option] of the given symbol.
     *
     * For [text][Text] symbols, consider using the appropriate overload.
     */
    public fun <S : Symbol> maybe(query: S): Option<S> = Option(query)

    // ------------------------------ repetitions ------------------------------

    /**
     * Returns a [repetition][Repetition] of the given symbol.
     *
     * For [text][Text] symbols, consider using the appropriate overload.
     */
    public fun <S : Symbol> multiple(query: S): Repetition<S> = Repetition(query)

    // ------------------------------ optional repetitions ------------------------------

    /**
     * Returns an optional repetition of the given symbol.
     */
    public fun <S : Symbol> any(query: S): Option<Repetition<S>> = maybe(multiple(query))

    // ------------------------------ junctions ------------------------------

    // Allow type-safe junctions and sequences to be root of new junction/sequence (types are checked anyway)

    /**
     * Returns a junction of the two symbols.
     */
    public infix fun <S1 : Symbol, S2 : Symbol> S1.or(option2: S2): Junction2<S1, S2> = toJunction(this, option2)

    protected fun <S1 : Symbol, S2 : Symbol> toJunction(option1: S1, option2: S2): Junction2<S1, S2> {
        val junction = Junction().apply {
            components += option1
            components += option2
        }
        return Junction2(junction.unsafeCast())
    }

    // ------------------------------ sequences ------------------------------

    /**
     * Returns a sequence containing the two symbols.
     */
    public operator fun <S1 : Symbol, S2 : Symbol> S1.plus(query2: S2): Sequence2<S1, S2> = toSequence(this, query2)

    protected fun <S1 : Symbol, S2 : Symbol> toSequence(query1: S1, query2: S2): Sequence2<S1, S2> {
        val sequence = Sequence().apply {
            components += query1
            components += query2
        }
        return Sequence2(sequence.unsafeCast())
    }
}

/**
 * Defines a scope where a [parser][Parser] without a lexer can be defined.
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
     * Assigns a [text symbol][Text] of the single character to the property being delegated to.
     */
    public operator fun Char.getValue(thisRef: Nothing?, symbol: KProperty<*>): NamedSymbol<Text> {
        return NamedSymbol(symbol.name, Text(this))
    }

    /**
     * Returns a [text][Text] symbol.
     */
    public final override fun text(query: String): Text = super.text(query).unsafeCast()

    /**
     * Returns a [character switch][Switch] symbol.
     *
     * Should be preferred over a [junction][or] of [text symbols][text] each with a single character.
     */
    public final override fun of(switch: String): Switch = super.of(switch).unsafeCast()

    // ------------------------------ options ------------------------------

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
     * Returns a junction of this text and the given symbol.
     */
    public infix fun <S2 : Symbol> Char.or(option2: S2): Junction2<Text, S2> = toJunction(Text(this), option2)

    /**
     * Returns a junction of this symbol and the given text.
     */
    public infix fun <S1 : Symbol> S1.or(option2: Char): Junction2<S1, Text> = toJunction(this, Text(option2))

    // ------------------------------ sequences ------------------------------

    /**
     * Returns a sequence containing this text and the given symbol.
     */
    public operator fun <S2 : Symbol> Char.plus(query2: S2): Sequence2<Text, S2> = toSequence(Text(this), query2)

    /**
     * Returns a sequence containing this symbol and the given text.
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

    // ------------------------------ text & switches ------------------------------

    /**
     * Assigns a [text][Text] fragment of the single character to the property being delegated to.
     */
    public operator fun Char.getValue(thisRef: Nothing?, symbol: KProperty<*>): NamedSymbol<LexerSymbol> {
        return NamedSymbol(symbol.name, LexerSymbol(Fragment(Text(this)))).also { lexerSymbols += it }
    }

    /**
     * Returns a [text][Text] fragment.
     */
    public final override fun text(query: String): Fragment = Fragment(Text(query))

    /**
     * Returns a [character switch][Switch] fragment.
     *
     * Should be preferred over a [junction][or] of [text][text] fragments each with a single character.
     */
    public final override fun of(switch: String): Fragment = Fragment(super.of(switch).unsafeCast())

    // ------------------------------ options ------------------------------

    /**
     * Return an [option][Option] of the given fragment.
     */
    public fun maybe(query: Fragment): Fragment = Fragment(maybe(query.root))

    /**
     * Returns a text [option][Option].
     */
    public fun maybe(query: String): Fragment = Fragment(Option(Text(query)))

    /**
     * Returns a text [option][Option].
     */
    public fun maybe(query: Char): Fragment = Fragment(Option(Text(query)))

    /**
     * Returns a switch [option][Option].
     */
    public fun maybeOf(switch: String): Fragment = Fragment(maybe(super.of(switch) as Switch))

    // ------------------------------ repetitions ------------------------------

    /**
     * Returns a [repetition][Repetition] of the given fragment.
     */
    public fun multiple(query: Fragment): Fragment = Fragment(multiple(query.root))

    /**
     * Returns a text [repetition][Repetition].
     */
    public fun multiple(query: String): Fragment = Fragment(Repetition(Text(query)))

    /**
     * Returns a text [repetition][Repetition].
     */
    public fun multiple(query: Char): Fragment = Fragment(Option(Text(query)))

    /**
     * Returns a switch [repetition][Repetition].
     */
    public fun multipleOf(switch: String): Fragment = Fragment(multiple(super.of(switch) as Switch))

    // ------------------------------ optional repetitions ------------------------------

    /**
     * Returns an optional repetition of the given fragment.
     */
    public fun any(query: Fragment): Fragment = Fragment(maybe(multiple(query.root)))

    /**
     * Returns an optional repetition of the text.
     */
    public fun any(query: String): Fragment = Fragment(maybe(multiple(Text(query))))

    /**
     * Returns an optional repetition of the text.
     */
    public fun any(query: Char): Fragment = Fragment(maybe(multiple(Text(query))))

    /**
     * Returns an optional repetition of the switch.
     */
    public fun anyOf(switch: String): Fragment = Fragment(any(super.of(switch) as Switch))

    // ------------------------------ junctions ------------------------------

    /**
     * Returns a junction of this text and the given fragment.
     */
    public infix fun Char.or(option2: Fragment): Fragment {
        if (option2 is JunctionFragment) {
            option2.root.unsafeCast<TypeSafeJunction<*>>().untyped
        }
        JunctionFragment(toJunction(Text(this), option2.root))
    }

    /**
     * Returns a junction of this fragment and the given text.
     */
    public infix fun Fragment.or(option2: Char): Fragment {
        if (this is JunctionFragment) {

        }
        JunctionFragment(toJunction(root, Text(option2)))
    }

    // ------------------------------ sequences ------------------------------

    /**
     * Returns a sequence containing this text and the given fragment.
     */
    public operator fun Char.plus(query2: Fragment): Fragment {
        if (query2 is SequenceFragment) {

        }
        SequenceFragment(toSequence(Text(this), query2.root))
    }

    /**
     * Returns a sequence containing this fragment and the given text.
     */
    public operator fun Fragment.plus(query2: Char): Fragment {
        if (this is SequenceFragment) {

        }
        SequenceFragment(toSequence(root, Text(query2)))
    }
}

public class NullaryParserDefinitionDsl internal constructor() : ParserDefinitionDsl() {
    internal val listeners = mutableMapOf<String, Listener<*>>()

    /**
     * Assigns an action to be performed whenever a successful match is made using this symbol.
     */
    public infix fun <T : NameableSymbol<T>> NamedSymbol<T>.listener(action: Listener<T>) {
        if (name in listeners) {
            raiseAmbiguousListener(name)
        }
        listeners[name] = action
    }
}


public open class UnaryParserDefinitionDsl<A> internal constructor() : ParserDefinitionDsl() {
    internal val listeners = mutableMapOf<String, ListenerWithArgument<*, A>>()

    internal var initializer: Consumer<A>? = null

    /**
     * Assigns an action to be performed whenever a successful match is made using this symbol.
     */
    public infix fun <T : NameableSymbol<T>> NamedSymbol<T>.listener(action: ListenerWithArgument<T, A>) {
        if (name in listeners) {
            raiseAmbiguousListener(name)
        }
        listeners[name] = action
    }
    /**
     * Describes the initialization logic of arguments supplied to this parser.
     */
    public fun init(initializer: Consumer<A>) {
        this.initializer?.let { throw MalformedParserException("Initializer defined more than once") }
        this.initializer = initializer
    }
}