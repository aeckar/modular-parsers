package io.github.aeckar.parsing

import io.github.aeckar.parsing.LexerSymbol.Behavior
import io.github.aeckar.parsing.LexerSymbol.Descriptor
import io.github.aeckar.parsing.LexerSymbol.Fragment
import io.github.aeckar.parsing.typesafe.*
import io.github.aeckar.parsing.utils.*
import kotlinx.collections.immutable.toImmutableList
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// ------------------------------------ abstract parser definitions ------------------------------------

/**
 * Defines a scope where [Parser] rules and symbolListeners can be defined.
 *
 * Each listener is invoked from a completed [abstract syntax tree][Parser.parse] in a top-down, left-to-right fashion.
 */
@ListenerDsl
public sealed class ParserDefinition {
    internal val startDelegate = OnceAssignable<Symbol, _>(raise = ::MalformedParserException)

    /**
     * The principal symbol to be matched.
     *
     * Leaving this property unassigned will not throw an exception for lexer-parsers,
     * except in the event that [Parser.parse] is invoked.
     * @throws MalformedParserException this property is left unassigned or is assigned a value more than once
     */
    public var start: Symbol by startDelegate

    internal val parserSymbols = mutableMapOf<String, NameableSymbol<*>>()      // Not including imports, implicits
    internal val inversionSymbols = mutableSetOf<Inversion>()

    private val implicitSymbols = mutableMapOf<String, NameableSymbol<*>?>()
    private val endSymbol = End()

    /**
     * @throws MalformedParserException the assertion fails
     */
    protected fun ensureUndefinedListener(name: String) {
        if (name in resolveListeners()) {
            throw MalformedParserException("Listener for symbol '$name' defined more than once")
        }
    }

    internal fun compileSymbols(): Map<String, NameableSymbol<*>> {
        val allSymbols = HashMap<String, NameableSymbol<*>>(parserSymbols.size + implicitSymbols.size)
        implicitSymbols.forEach { (name, symbol) ->
            symbol ?: throw MalformedParserException("Implicit symbol '$name' is undefined")
            allSymbols[name] = symbol
        }
        allSymbols += parserSymbols
        return allSymbols
    }

    internal fun resolveListeners() = when (this) {
        is NullaryParserDefinition -> (this as NullaryParserDefinition).resolveListeners()
        is UnaryParserDefinition<*> -> (this as UnaryParserDefinition<*>).resolveListeners()
    }

    // ------------------------------ symbol definition & import/export ------------------------------

    /**
     * Assigns a name to this symbol.
     *
     * Doing this for multiple named symbols is legal.
     */
    public operator fun <S : NameableSymbol<out S>> NameableSymbol<out S>.provideDelegate(
        thisRef: Any?,
        property: KProperty<*>
    ): ReadOnlyProperty<Nothing?, NamedSymbol<S>> {
        val name = property.name
        if (this is TypeUnsafeSymbol<*, *>) {  // Ensure implicit symbol itself is not used for parsing
            implicitSymbols[name] = null
        } else {
            if (this is Inversion) {    // Each requires definition of origin parser
                inversionSymbols += this
            }
            parserSymbols[name] = this
        }
        return NamedSymbol(name, this).readOnlyProperty()
    }

    /**
     * Imports the symbol with the given name from this parser for a single use.
     * @throws NoSuchElementException the symbol is undefined
     */
    public operator fun NamedNullaryParser.get(symbolName: String): Symbol {
        return resolveSymbols()[symbolName]?.let { NullaryForeignSymbol(NamedSymbol(symbolName, it), this) }
            ?: raiseUndefinedImport(symbolName)
    }

    /**
     * See [get] for details.
     */
    public operator fun NamedUnaryParser<*>.get(symbolName: String): Symbol {
        return resolveSymbols()[symbolName]?.let { UnaryForeignSymbol(NamedSymbol(symbolName, it), this) }
            ?: raiseUndefinedImport(symbolName)
    }

    /**
     * Allows the importing of a symbol from another named parser.
     * @param UnnamedT the type of the specified symbol
     */
    public fun <UnnamedT : NameableSymbol<out UnnamedT>> NamedNullaryParser.import(
    ): NullarySymbolImport<UnnamedT> {
        return NullarySymbolImport(this)
    }

    /**
     * See [import] for details.
     */
    public fun <UnnamedT : NameableSymbol<out UnnamedT>, ArgumentT> NamedUnaryParser<ArgumentT>.import(
    ): UnarySymbolImport<UnnamedT, ArgumentT> {
        return UnarySymbolImport(this)
    }

    /**
     * Delegating an instance of this class to a property assigns it the
     * [Symbol] of the same name defined in another named parser.
     *
     * If the symbol is not defined in the parser, a [MalformedParserException] will be thrown upon delegation.
     */
    public abstract inner class SymbolImport<ForeignT : ForeignSymbol<*>> : Nameable {
        internal abstract val origin: Parser

        protected fun resolveSymbol(name: String): NamedSymbol<*> {
            val symbol = try {
                origin.resolveSymbols().getValue(name)
            } catch (e: NoSuchElementException) {
                throw MalformedParserException("Symbol '$name' is not defined in parser '$origin'", e)
            }
            parserSymbols[name] = symbol
            return NamedSymbol(name, symbol)
        }
    }

    /**
     * Ensures that the specified extension listener can be created.
     * @throws MalformedParserException the assertion fails
     */
    internal fun Parser.ensureExtensionCandidate(name: String) {
        ensureUndefinedListener(name)
        if (name !in resolveListeners()) {
            throw MalformedParserException("Cannot extend undefined listener for symbol '$name'")
        }
    }

    /**
     * Used to import a symbol from a [NullaryParser].
     */
    public inner class NullarySymbolImport<UnnamedT : NameableSymbol<out UnnamedT>> internal constructor(
        override val origin: NullaryParser
    ) : SymbolImport<NullaryForeignSymbol<UnnamedT>>() {
        override fun provideDelegate(
            thisRef: Any?,
            property: KProperty<*>
        ): ReadOnlyProperty<Any?, NullaryForeignSymbol<UnnamedT>> {
            return NullaryForeignSymbol<UnnamedT>(resolveSymbol(property.name).fragileUnsafeCast(), origin)
                .readOnlyProperty()
        }
    }

    /**
     * Used to import a symbol from a [UnaryParser].
     */
    public inner class UnarySymbolImport<UnnamedT : NameableSymbol<out UnnamedT>, ArgumentT> internal constructor(
        override val origin: UnaryParser<ArgumentT>
    ) : SymbolImport<UnaryForeignSymbol<UnnamedT, ArgumentT>>() {
        override fun provideDelegate(
            thisRef: Any?,
            property: KProperty<*>
        ): ReadOnlyProperty<Any?, UnaryForeignSymbol<UnnamedT, ArgumentT>> {
            return UnaryForeignSymbol<UnnamedT, ArgumentT>(resolveSymbol(property.name).fragileUnsafeCast(), origin)
                .readOnlyProperty()
        }
    }

    private fun NamedParser.raiseUndefinedImport(symbolName: String): Nothing {
        throw MalformedParserException("Symbol '$symbolName' is undefined in parser '$name'")
    }

    // ------------------------------ symbol inversions & implicit symbols ------------------------------

    /**
     * Returns an [Inversion] of this symbol.
     */
    public operator fun NamedSymbol<*>.not(): Inversion = Inversion(this)

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
            unnamed = value.typeUnsafe.unsafeCast()
            implicitSymbols[name] = unnamed
        }

    /**
     * Returns a junction that can be defined after being delegated to a property.
     *
     * Because the types of the options are erased, they cannot be accessed within symbolListeners.
     */
    public fun junction(): TypeUnsafeJunction<*> = TypeUnsafeJunction()

    /**
     * Returns a sequence that can be defined after being delegated to a property.
     *
     * Because the types of the queries are erased, they cannot be accessed within symbolListeners.
     */
    public fun sequence(): TypeUnsafeSequence<*> = TypeUnsafeSequence()

    // ------------------------------ text & switches ------------------------------

    /**
     * Returns the switch literal inverse to this string.
     */
    public operator fun String.not(): String = this.toRanges().invertRanges().rangesToString()

    /**
     * Returns a [Text] symbol.
     */
    protected open fun text(query: String): ParserComponent = Text(query)


    /**
     * Returns a [character switch][Switch] symbol.
     *
     * To represent `'-'` as a character, use the escape `/-`.
     * Similarly, to represent `'/'`, the escape `//` must be used.
     *
     * Ranges may be inverted by invoking the [not] operator on the supplied literal.
     *
     * Should be preferred over a junction of [text symbols][text] each with a single character.
     */
    protected open fun of(switch: String): ParserComponent = Switch(switch, switch.toRanges().optimizeRanges())

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
     * Returns a junction of the two symbols.
     */
    public infix fun <S1 : Symbol, S2 : Symbol> S1.or(option2: S2): Junction2<S1, S2> = toJunction(this, option2)

    protected fun <S1 : Symbol, S2 : Symbol> toJunction(option1: S1, option2: S2): Junction2<S1, S2> {
        return Junction2(TypeUnsafeJunction(option1, option2).unsafeCast())
    }

    // ------------------------------ sequences ------------------------------

    /**
     * Returns a sequence containing the two symbols.
     */
    public operator fun <S1 : Symbol, S2 : Symbol> S1.plus(query2: S2): Sequence2<S1, S2> = toSequence(this, query2)

    protected fun <S1 : Symbol, S2 : Symbol> toSequence(query1: S1, query2: S2): Sequence2<S1, S2> {
        return Sequence2(TypeUnsafeSequence(query1, query2).unsafeCast())
    }

    // ------------------------------ end of input ------------------------------

    /**
     * Returns an [End] symbol.
     */
    public fun end(): End = endSymbol
}

/*
    Junctions and sequences between two characters are not implemented because
    users can simply use a character switch instead.
 */

/**
 * A [definition][ParserDefinition] of a parser without an argument.
 */
public sealed interface NullaryParserDefinition {
    /**
     * Assigns the supplied listener to the symbol.
     *
     * Whenever a match is made to this symbol, the listener is invoked.
     */
    public infix fun <MatchT : NameableSymbol<out MatchT>> NamedSymbol<out MatchT>.listener(
        action: NullarySymbolListener<MatchT>
    )

    /**
     * Assigns the supplied listener to the symbol.
     *
     * Whenever a match is made to this symbol,
     * the listener previously defined for this symbol is invoked before this one is.
     */
    public infix fun <MatchT : NameableSymbol<out MatchT>> NullaryForeignSymbol<out MatchT>.extendsListener(
        action: NullarySymbolListener<MatchT>
    )
}


internal fun NullaryParserDefinition.resolveListeners(): Map<String, NullarySymbolListener<*>> = when (this) {
    is NullaryLexerlessParserDefinition -> symbolListeners
    is NullaryLexerParserDefinition -> lexerless.symbolListeners
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
    public infix fun <MatchT : NameableSymbol<out MatchT>> NamedSymbol<out MatchT>.listener(
        action: UnarySymbolListener<MatchT, ArgumentT>
    )

    /**
     * Assigns the supplied listener to the symbol.
     *
     * Whenever a match is made to this symbol,
     * the listener previously defined for this symbol is invoked before this one is.
     */
    public infix fun <MatchT : NameableSymbol<out MatchT>> NullaryForeignSymbol<out MatchT>.extendsListener(
        action: UnarySymbolListener<MatchT, ArgumentT>
    )

    /**
     * See [extendsListener] for details.
     */
    public infix fun <MatchT : NameableSymbol<MatchT>>
    UnaryForeignSymbol<MatchT, ArgumentT>.extendsListener(
        action: UnarySymbolListener<MatchT, ArgumentT>
    )

    /**
     * Describes the initialization logic of the argument supplied to this parser.
     */
    public fun init(initializer: (ArgumentT) -> Unit)
}

internal fun <ArgumentT>
        UnaryParserDefinition<ArgumentT>.resolveListeners(): Map<String, UnarySymbolListener<Symbol, ArgumentT>> =
    when (this) {
        is UnaryLexerlessParserDefinition<*> -> symbolListeners.unsafeCast()
        is UnaryLexerParserDefinition<*> -> lexerless.symbolListeners.unsafeCast()
    }

// ------------------------------------ lexerless parser definitions ------------------------------------

/**
 * Defines a scope where a [Parser] without a lexer can be defined.
 */
public sealed class LexerlessParserDefinition : ParserDefinition() {
    internal val skipDelegate = OnceAssignable<Symbol, _>(raise = ::MalformedParserException)

    /**
     * The symbol whose matches are discarded during parsing.
     *
     * Whenever a successful match is made, whatever is then matched to this symbol is ignored.
     * @throws MalformedParserException this property is assigned a value more than once
     */
    public var skip: Symbol by skipDelegate

    // ------------------------------ text & switches ------------------------------

    /**
     * Assigns a [Text] symbol of the single character to the property being delegated to.
     */
    public operator fun Char.provideDelegate(
        thisRef: Any?,
        symbol: KProperty<*>
    ): ReadOnlyProperty<Any?, NamedSymbol<Text>> {
        return NamedSymbol(symbol.name, Text(this)).readOnlyProperty()
    }

    public final override fun text(query: String): Text = super.text(query).unsafeCast()
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
 * Defines a scope where a [Parser] without a lexer that does not take an argument can be defined.
 */
public class NullaryLexerlessParserDefinition internal constructor(
) : LexerlessParserDefinition(), NullaryParserDefinition {
    internal val symbolListeners = mutableMapOf<String, NullarySymbolListener<*>>()

    /**
     * Assigns an action to be performed whenever a successful match is made using this symbol.
     */
    override fun <MatchT : NameableSymbol<out MatchT>> NamedSymbol<out MatchT>.listener(
        action: NullarySymbolListener<MatchT>
    ) {
        ensureUndefinedListener(name)
        symbolListeners[name] = action
    }

    override fun <MatchT : NameableSymbol<out MatchT>> NullaryForeignSymbol<out MatchT>.extendsListener(
        action: NullarySymbolListener<MatchT>
    ) {
        origin.ensureExtensionCandidate(name)
        symbolListeners[name] = NullarySymbolListener {
            with(origin.resolveListeners().getValue(name).unsafeCast<NullarySymbolListener<MatchT>>()) {
                this@NullarySymbolListener()
            }
            with(action) { this@NullarySymbolListener() }
        }
    }
}

/**
 * Defines a scope where a [Parser] without a lexer that takes one argument can be defined.
 */
public class UnaryLexerlessParserDefinition<ArgumentT> internal constructor(
) : LexerlessParserDefinition(), UnaryParserDefinition<ArgumentT> {
    internal val symbolListeners = mutableMapOf<String, UnarySymbolListener<*, ArgumentT>>()

    internal var initializer: ((ArgumentT) -> Unit)? = null

    /**
     * Assigns an action to be performed whenever a successful match is made using this symbol.
     */
    override fun <MatchT : NameableSymbol<out MatchT>> NamedSymbol<out MatchT>.listener(
        action: UnarySymbolListener<MatchT, ArgumentT>
    ) {
        ensureUndefinedListener(name)
        symbolListeners[name] = action
    }

    override fun <MatchT : NameableSymbol<out MatchT>> NullaryForeignSymbol<out MatchT>.extendsListener(
        action: UnarySymbolListener<MatchT, ArgumentT>
    ) {
        origin.ensureExtensionCandidate(name)
        symbolListeners[name] = UnarySymbolListener {
            with(origin.resolveListeners().getValue(name).unsafeCast<NullarySymbolListener<MatchT>>()) {
                this@UnarySymbolListener()
            }
            with(action) { this@UnarySymbolListener(it) }
        }
    }

    override fun <MatchT : NameableSymbol<MatchT>>
    UnaryForeignSymbol<MatchT, ArgumentT>.extendsListener(
        action: UnarySymbolListener<MatchT, ArgumentT>
    ) {
        origin.ensureExtensionCandidate(name)
        symbolListeners[name] = UnarySymbolListener {
            with(origin.resolveListeners().getValue(name).unsafeCast<UnarySymbolListener<MatchT, ArgumentT>>()) {
                this@UnarySymbolListener(it)
            }
            with(action) { this@UnarySymbolListener(it) }
        }
    }

    /**
     * Describes the initialization logic of arguments supplied to this parser.
     */
    override fun init(initializer: (ArgumentT) -> Unit) {
        this.initializer?.let { throw MalformedParserException("Initializer defined more than once") }
        this.initializer = initializer
    }
}

// ------------------------------------ lexer-parser definitions ------------------------------------

/**
 * Defines a scope where a [NameableLexerParser] can be defined.
 */
public sealed class LexerParserDefinition : ParserDefinition() {
    internal val recoveryDelegate = OnceAssignable<LexerComponent, _>(::MalformedParserException)

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
    public val recovery: LexerComponent by recoveryDelegate

    /**
     * The lexer symbols to be ignored during lexical analysis.
     *
     * Nodes produced by these symbols will not be present in the list returned by [NameableLexerParser.tokenize].
     * @throws MalformedParserException this property is left unassigned, or is assigned a value more than once
     */
    public val skip: MutableList<NamedSymbol<LexerSymbol>> = mutableListOf()

    internal val lexerModes = mutableMapOf<String, MutableList<NamedSymbol<LexerSymbol>>>()

    private var mode = ""   // default lexer mode name

    // ------------------------------ lexer modes -------------------------

    /**
     * Signals that all lexer symbols defined hereafter and before the next mode
     * should be used exclusively when in the specified lexer mode.
     * @throws MalformedParserException [name] is invalid according to [isValidModeName]
     */
    public fun mode(name: String) {
        if (!name.isValidModeName()) {
            throw MalformedParserException("'$name' is an invalid lexer mode name")
        }
        mode = name
    }

    /**
     * Signals that all lexer symbols defined hereafter and before the next mode
     * should be used exclusively when in the default (initial) lexer mode.
     */
    public fun defaultMode() {
        mode = ""
    }

    /**
     * Specifies that the given behavior will be invoked whenever
     * the lexer symbol produced by the returned descriptor produces a token.
     */
    public infix fun Fragment.does(behavior: Behavior): Descriptor = Descriptor(this, behavior)

    /**
     * Returns a behavior that pushes the specified mode to the lexer mode stack.
     */
    public fun push(mode: String): Behavior = Behavior { add(mode) }

    /**
     * Returns a behavior that pops the top mode from the lexer mode stack.
     */
    public fun pop(): Behavior =  Behavior { removeLast() }

    /**
     * Returns a behavior that replaces the top of the lexer mode stack with the specified mode.
     */
    public fun set(mode: String): Behavior = Behavior { this[lastIndex] = mode }

    // ------------------------------ symbol definition ------------------------------

    /**
     * Assigns a [LexerSymbol] matching this fragment to the property being delegated to.
     */
    public operator fun Fragment.provideDelegate(
        thisRef: Any?,
        symbol: KProperty<*>
    ): ReadOnlyProperty<Nothing?, NamedSymbol<LexerSymbol>> {
        return NamedSymbol(symbol.name, LexerSymbol(this)).readOnlyProperty()
    }

    /**
     * Assigns a [LexerSymbol] matching this descriptor to the property being delegated to.
     */
    public operator fun Descriptor.provideDelegate(
        thisRef: Any?,
        symbol: KProperty<*>
    ): ReadOnlyProperty<Nothing?, NamedSymbol<LexerSymbol>> {
        return NamedSymbol(symbol.name, LexerSymbol(fragment, behavior)).readOnlyProperty()
    }

    // ------------------------------ text & switches ------------------------------

    /**
     * Assigns a [LexerSymbol] matching this character to the property being delegated to.
     */
    public operator fun Char.provideDelegate(
        thisRef: Any?,
        symbol: KProperty<*>
    ): ReadOnlyProperty<Any?, NamedSymbol<LexerSymbol>> {
        val named = NamedSymbol(symbol.name, LexerSymbol(Fragment(Text(this))))
        lexerModes.getValue(mode) += named
        return named.readOnlyProperty()
    }

    public final override fun text(query: String): Fragment = Fragment(Text(query))
    public final override fun of(switch: String): Fragment {
        return Fragment(super.of(switch).unsafeCast())
    }

    // ------------------------------ options ------------------------------

    /**
     * Return an [Option] of the given fragment.
     */
    public fun maybe(query: Fragment): Fragment = Fragment(maybe(query.root))

    /**
     * Returns a text [Option].
     */
    public fun maybe(query: String): Fragment = Fragment(Option(Text(query)))

    /**
     * Returns a text [Option].
     */
    public fun maybe(query: Char): Fragment = Fragment(Option(Text(query)))

    /**
     * Returns a switch [Option].
     */
    public fun maybeOf(switch: String): Fragment = Fragment(maybe(super.of(switch) as Switch))

    // ------------------------------ repetitions ------------------------------

    /**
     * Returns a [Repetition] of the given fragment.
     */
    public fun multiple(query: Fragment): Fragment = Fragment(multiple(query.root))

    /**
     * Returns a text [Repetition].
     */
    public fun multiple(query: String): Fragment = Fragment(Repetition(Text(query)))

    /**
     * Returns a text [Repetition].
     */
    public fun multiple(query: Char): Fragment = Fragment(Option(Text(query)))

    /**
     * Returns a switch [Repetition].
     */
    public fun multipleOf(switch: String): Fragment {
        return Fragment(multiple(super.of(switch) as Switch))
    }

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
     * Returns a junction of the two fragments.
     */
    public infix fun Fragment.or(option2: Fragment): Fragment {
        val other = option2.root
        if (root is TypeUnsafeJunction<*>) {
            root.components += other
            return this
        }
        if (other is TypeUnsafeJunction<*>) {
            other.components += root
            return option2
        }
        return Fragment(TypeUnsafeJunction(root, other))
    }

    /**
     * Returns a junction of this text and the given fragment.
     */
    public infix fun Char.or(option2: Fragment): Fragment {
        val other = option2.root
        if (other is TypeUnsafeJunction<*>) {
            other.components += Text(this)
            return option2
        }
        return Fragment(TypeUnsafeJunction(Text(this), other))
    }

    /**
     * Returns a junction of this fragment and the given text.
     */
    public infix fun Fragment.or(option2: Char): Fragment {
        if (root is TypeUnsafeJunction<*>) {
            root.components += Text(option2)
            return this
        }
        return Fragment(TypeUnsafeJunction(Text(option2), root))
    }

    // ------------------------------ sequences ------------------------------

    /**
     * Returns a sequence containing the two fragments
     */
    public operator fun Fragment.plus(query2: Fragment): Fragment {
        val other = query2.root
        if (root is TypeUnsafeSequence<*>) {
            root.components += other
            return this
        }
        if (other is TypeUnsafeSequence<*>) {
            other.components += root
            return query2
        }
        return Fragment(TypeUnsafeSequence(root, other))
    }

    /**
     * Returns a sequence containing this text and the given fragment.
     */
    public operator fun Char.plus(query2: Fragment): Fragment {
        val other = query2.root
        if (other is TypeUnsafeSequence<*>) {
            other.components += Text(this)
            return query2
        }
        return Fragment(TypeUnsafeSequence(Text(this), other))
    }

    /**
     * Returns a sequence containing this fragment and the given text.
     */
    public operator fun Fragment.plus(query2: Char): Fragment {
        if (root is TypeUnsafeSequence<*>) {
            root.components += Text(query2)
            return this
        }
        return Fragment(TypeUnsafeSequence(Text(query2), root))
    }

    private companion object {
        /**
         * A list of character ranges that a character must satisfy to be
         * the first character in a [Kotlin identifier][isValidModeName].
         */
        val modeNameStart: List<CharRange> = "a-zA-Z_".toRanges().toImmutableList()

        /**
         * A list of character ranges that a character must satisfy to be
         * any character besides the first one in a [Kotlin identifier][isValidModeName].
         */
        val modeNamePart: List<CharRange> = "a-ZA-Z0-9_".toRanges().toImmutableList()

        /**
         * Returns true if this contains only a valid Kotlin identifier.
         */
        fun String.isValidModeName(): Boolean {
            return this.isNotEmpty()
                    && this[0] satisfies modeNameStart
                    && takeLast(length - 1).all { it satisfies modeNamePart }
        }
    }
}

/**
 * Defines a scope where a [NameableLexerParser] that does not take an argument can be defined.
 */
public class NullaryLexerParserDefinition private constructor(
    internal val lexerless: NullaryLexerlessParserDefinition
) : LexerParserDefinition(), NullaryParserDefinition by lexerless {
    internal constructor() : this(NullaryLexerlessParserDefinition())
}

/**
 * Defines a scope where a [NameableLexerParser] that takes one argument can be defined.
 */
public class UnaryLexerParserDefinition<in ArgumentT> private constructor(
    internal val lexerless: UnaryLexerlessParserDefinition<@UnsafeVariance ArgumentT>
) : LexerParserDefinition(), UnaryParserDefinition<@UnsafeVariance ArgumentT> by lexerless {
    internal constructor() : this(UnaryLexerlessParserDefinition())
}