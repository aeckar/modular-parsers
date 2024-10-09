package io.github.aeckar.parsing

import io.github.aeckar.parsing.LexerSymbol.Behavior
import io.github.aeckar.parsing.LexerSymbol.Descriptor
import io.github.aeckar.parsing.LexerSymbol.Fragment
import io.github.aeckar.parsing.OperatorDefinition.ReturnDescriptor
import io.github.aeckar.parsing.typesafe.*
import io.github.aeckar.parsing.utils.*
import kotlinx.collections.immutable.toImmutableList
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal const val DEFAULT_MODE_NAME = ""

/*
    ---- class hierarchy ----
    OperatorDefinition (i)

    ParserDefinition (sc)
        LexerlessParserDefinition (oc)
            LexerlessParserOperatorDefinition : OperatorDefinition (c)
        LexerParserDefinition (oc)
            LexerParserOperatorDefinition : OperatorDefinition (c)

    ---- naming scheme ----
    class ...Properties = Component of other classes (inheritance by composition)
    class ...Descriptor = Type of a specific return value
    fun   raise...      = Always throws unrecoverable exception

    ---- notes ----
    The logic of operators should remain separate from those of the various parser (definition) types,
    except in their property classes.

        How to "inherit" multiple classes:
        - Inheritance by composition
        - Extension functions for public-facing functions
        - Internal functions in base class for internal functions
        - Abstract properties with default behavior throwing an exception

    It's a bit trickier with a nameable/named dichotomy...
 */

internal fun raiseNonOperator(): Nothing {
    throw UnsupportedOperationException("Operator properties unsupported for non-operator parsers")
}

// ------------------------------------ operator definitions ------------------------------------

/**
 * Defines the configuration of an [Operator].
 *
 * @param R the type of the value returned by the operator being defined.
 */
public sealed interface OperatorDefinition<R> {
    /**
     * Contains the type information of the value returned by an operator
     */
    @Suppress("unused")
    public class ReturnDescriptor<R> private constructor() {
        internal companion object {
            val INSTANCE: ReturnDescriptor<*> = ReturnDescriptor<Nothing>()
        }
    }
}

/**
 * Signals that this operator returns an instance of [R], as returned by [lazyValue].
 */
@Suppress("UNCHECKED_CAST")
public fun <R> OperatorDefinition<R>.returns(lazyValue: () -> R): ReturnDescriptor<R> {
    try {
        (this as ParserDefinition).operator.returnValue = lazyValue as () -> Nothing
    } catch (e: IllegalStateException) {
        throw MalformedParserException("Ambiguous return value", e)
    }
    return ReturnDescriptor.INSTANCE as ReturnDescriptor<R>
}

// ------------------------------------ parser-operator definitions ------------------------------------

internal class OperatorDefinitionProperties<R> {
    internal val returnValueDelegate = OnceAssignable<() -> R>(::IllegalStateException)
    internal var returnValue by returnValueDelegate
    internal val listeners = mutableMapOf<String, Listener<*>>()

    internal fun ensureUndefinedListener(name: String) {
        if (name in listeners) {
            throw MalformedParserException("Listener for symbol '$name' defined more than once")
        }
    }

    /**
     * Ensures that the specified extension listener can be created.
     * @throws MalformedParserException the assertion fails
     */
    internal fun ensureExtensionCandidate(name: String, parser: Parser) {
        ensureUndefinedListener(name)
        if (name !in parser.operator<Any?>().listenersMap) {
            throw MalformedParserException("Cannot extend undefined listener for symbol '$name'")
        }
    }
}

/**
 * Defines a combined parser-operator [without a lexer][NameableLexerlessParserOperator].
 */
public class LexerlessParserOperatorDefinition<R> internal constructor(
) : LexerlessParserDefinition(), OperatorDefinition<R> {
    override val operator = OperatorDefinitionProperties<R>()

    public override fun <MatchT : NameableSymbol<out MatchT>> NamedSymbol<out MatchT>.listener(
        action: Listener<MatchT>
    ) {
        defineListener(this, action)
    }

    public override fun <MatchT : NameableSymbol<out MatchT>> ForeignSymbol<out MatchT>.extendsListener(
        action: Listener<MatchT>
    ) {
        defineExtendsListener(this, action)
    }
}

/**
 * Defines a combined parser-operator [with a lexer][NameableLexerParserOperator].
 */
public class LexerParserOperatorDefinition<R> internal constructor() : LexerParserDefinition(), OperatorDefinition<R> {
    override val operator = OperatorDefinitionProperties<R>()

    public override fun <MatchT : NameableSymbol<out MatchT>> NamedSymbol<out MatchT>.listener(
        action: Listener<MatchT>
    ) {
        defineListener(this, action)
    }

    public override fun <MatchT : NameableSymbol<out MatchT>> ForeignSymbol<out MatchT>.extendsListener(
        action: Listener<MatchT>
    ) {
        defineExtendsListener(this, action)
    }
}

// ------------------------------------ parser definitions ------------------------------------

/**
 * Defines the configuration of a [Parser].
 */

public sealed class ParserDefinition {
    internal val startDelegate = OnceAssignable<Symbol>(::MalformedParserException)

    /**
     * The principal symbol to be matched.
     *
     * Leaving this property unassigned will not throw an exception for lexer-parsers,
     * except in the event that [Parser.parse] is invoked.
     * @throws MalformedParserException this property is left unassigned or is assigned a value more than once
     */
    public var start: Symbol by startDelegate

    /**
     * Does not include implicit nor imported symbols.
     */
    internal val parserSymbols = mutableMapOf<String, NameableSymbol<*>>()

    internal val inversionSymbols = mutableSetOf<Inversion>()
    internal open val operator: OperatorDefinitionProperties<*> get() = raiseNonOperator()

    private val implicitSymbols = mutableMapOf<String, NameableSymbol<*>?>()
    private val endSymbol = End()

    internal fun compileSymbols(): Map<String, NameableSymbol<*>> {
        val allSymbols = HashMap<String, NameableSymbol<*>>(parserSymbols.size + implicitSymbols.size)
        implicitSymbols.forEach { (name, symbol) ->
            symbol ?: throw MalformedParserException("Implicit symbol '$name' is undefined")
            allSymbols[name] = symbol
        }
        allSymbols += parserSymbols
        return allSymbols
    }

    // ------------------------------ operator-exclusive ------------------------------

    /**
     * Assigns the supplied listener to the symbol.
     *
     * Whenever a match is made to this symbol, the listener is invoked.
     */
    protected open infix fun <MatchT : NameableSymbol<out MatchT>> NamedSymbol<out MatchT>.listener(
        action: Listener<MatchT>
    ) {
        defineListener(this, action)
    }   // Cannot make extension until context parameters are available

    /**
     * Assigns the supplied listener to the symbol.
     *
     * Whenever a match is made to this symbol,
     * the listener previously defined for this symbol is invoked before this one is.
     */
    protected open infix fun <MatchT : NameableSymbol<out MatchT>> ForeignSymbol<out MatchT>.extendsListener(
        action: Listener<MatchT>
    ) {
        defineExtendsListener(this, action)
    }

    protected fun <MatchT : NameableSymbol<out MatchT>> defineListener(
        symbol: NamedSymbol<out MatchT>,
        action: Listener<MatchT>
    ) {
        operator.ensureUndefinedListener(symbol.name)
        operator.listeners[symbol.name] = action
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <MatchT : NameableSymbol<out MatchT>> defineExtendsListener(
        symbol: ForeignSymbol<out MatchT>,
        action: Listener<MatchT>
    ) {
        val def = this@ParserDefinition.operator
        val operator = symbol.origin.operator<Any?>()
        def.ensureExtensionCandidate(symbol.name, symbol.origin)
        operator.listenersMap[symbol.name] = Listener {
            with(operator.listenersMap.getValue(symbol.name) as Listener<MatchT>) {
                this@Listener()
            }
            with(action) { this@Listener() }
        }
    }

    // ------------------------------ symbol definition & import/export ------------------------------

    /**
     * Assigns a name to this symbol.
     *
     * Doing this for multiple named symbols is legal.
     */
    @Suppress("unused")
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
        return NamedSymbol(name, this).toNamedProperty()
    }

    /**
     * Imports the symbol with the given name from this parser for a single use.
     * @throws NoSuchElementException the symbol is undefined
     */
    public operator fun Parser.get(symbolName: String): Symbol {
        return parserSymbols()[symbolName]?.let { ForeignSymbol(NamedSymbol(symbolName, it), this) }
            ?: throw MalformedParserException("Symbol '$symbolName' is undefined")
    }

    /**
     * Allows the importing of a symbol from another named parser.
     * @param UnnamedT the type of the specified symbol
     */
    public fun <UnnamedT : NameableSymbol<UnnamedT>> Parser.import(
    ): ImportDescriptor<UnnamedT> {
        return ImportDescriptor(this)
    }

    /**
     * Delegating an instance of this class to a property assigns it the
     * [Symbol] of the same name defined in another named parser.
     *
     * If the symbol is not defined in the parser, a [MalformedParserException] will be thrown upon delegation.
     */
    @Suppress("UNCHECKED_CAST")
    public inner class ImportDescriptor<UnnamedT : NameableSymbol<UnnamedT>> internal constructor(
        internal val origin: Parser
    ) : Nameable {
        override fun provideDelegate(
            thisRef: Any?,
            property: KProperty<*>
        ): ReadOnlyProperty<Any?, ForeignSymbol<UnnamedT>> {
            return ForeignSymbol(resolveSymbol(property.name) as NamedSymbol<out UnnamedT>, origin)
                .toNamedProperty()
        }

        private fun resolveSymbol(name: String): NamedSymbol<*> {
            val symbol = try {
                origin.parserSymbols().getValue(name)
            } catch (e: NoSuchElementException) {
                throw MalformedParserException("Symbol '$name' is not defined", e)
            }
            parserSymbols[name] = symbol
            return NamedSymbol(name, symbol)
        }
    }

    // ------------------------------ inversion & implicit symbol factories ------------------------------

    /**
     * The definition of this implicit symbol.
     *
     * If not assigned at least once, a [MalformedParserException] is thrown after parser initialization.
     * @throws MalformedParserException this property is accessed before it is assigned a value
     */
    @Suppress("UNCHECKED_CAST")
    public var <TypeSafeT : TypeSafeSymbol<*, *>, TypeUnsafeT : TypeUnsafeSymbol<out TypeSafeT, out TypeUnsafeT>>
    NamedSymbol<out TypeUnsafeT>.actual: TypeSafeT
        get() {
            return try {
                unnamed as TypeSafeT
            } catch (e: ClassCastException) {
                throw MalformedParserException("Definition of '$name' accessed before it was defined", e)
            }
        }
        set(value) {
            unnamed = value.typeUnsafe as NameableSymbol<Nothing>
            implicitSymbols[name] = unnamed
        }

    /**
     * Returns an [Inversion] of this symbol.
     */
    public operator fun NameableSymbol<*>.not(): Inversion = Inversion(this)

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

    // ------------------------------ other symbol factories ------------------------------

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

    /**
     * Returns an [Option] of the given symbol.
     *
     * For [Text] symbols, consider using the appropriate overload.
     */
    public fun <QueryT : Symbol> maybe(query: QueryT): Option<QueryT> = Option(query)

    /**
     * Returns a [Repetition] of the given symbol.
     *
     * For [Text] symbols, consider using the appropriate overload.
     */
    public fun <QueryT : Symbol> multiple(query: QueryT): Repetition<QueryT> = Repetition(query)

    /**
     * Returns an optional repetition of the given symbol.
     */
    public fun <QueryT : Symbol> any(query: QueryT): Option<Repetition<QueryT>> = maybe(multiple(query))

    /**
     * Returns an [End] symbol.
     */
    public fun end(): End = endSymbol

    // Allow all other type-safe junctions and sequences to use these factories
    // If IDE shows errors, run `generateTypeSafe` Gradle task

    /**
     * Returns a junction of the two symbols.
     */
    public infix fun <S1 : Symbol, S2 : Symbol> S1.or(option2: S2): Junction2<S1, S2> = toJunction(this, option2)

    /**
     * Returns a sequence containing the two symbols.
     */
    public operator fun <S1 : Symbol, S2 : Symbol> S1.plus(query2: S2): Sequence2<S1, S2> = toSequence(this, query2)

    protected fun <S1 : Symbol, S2 : Symbol> toJunction(option1: S1, option2: S2): Junction2<S1, S2> {
        return Junction2(TypeUnsafeJunction(option1, option2))
    }

    protected fun <S1 : Symbol, S2 : Symbol> toSequence(query1: S1, query2: S2): Sequence2<S1, S2> {
        return Sequence2(TypeUnsafeSequence(query1, query2))
    }
}

/**
 * Defines a [Parser] without a lexer.
 */
public open class LexerlessParserDefinition internal constructor() : ParserDefinition() {
    internal val skipDelegate = OnceAssignable<Symbol>(::MalformedParserException)

    /**
     * The symbol whose matches are discarded during parsing.
     *
     * Whenever a successful match is made, whatever is then matched to this symbol is ignored.
     * @throws MalformedParserException this property is assigned a value more than once
     */
    public var skip: Symbol by skipDelegate

    // ------------------------------ text & switch factories ------------------------------

    /**
     * Assigns a [Text] symbol of the single character to the property being delegated to.
     */
    @Suppress("unused")
    public operator fun Char.provideDelegate(
        thisRef: Any?,
        symbol: KProperty<*>
    ): ReadOnlyProperty<Any?, NamedSymbol<Text>> {
        return NamedSymbol(symbol.name, Text(this)).toNamedProperty()
    }

    public override fun text(query: String): Text = super.text(query) as Text
    public override fun of(switch: String): Switch = super.of(switch) as Switch

    // ------------------------------ option factories ------------------------------

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

    // ------------------------------ repetition factories ------------------------------

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

    // ------------------------------ optional repetition factories ------------------------------

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

    // ------------------------------ junction factories ------------------------------

    /**
     * Returns a junction of this text and the given symbol.
     */
    public infix fun <S2 : Symbol> Char.or(option2: S2): Junction2<Text, S2> = toJunction(Text(this), option2)

    /**
     * Returns a junction of this symbol and the given text.
     */
    public infix fun <S1 : Symbol> S1.or(option2: Char): Junction2<S1, Text> = toJunction(this, Text(option2))

    // ------------------------------ sequence factories ------------------------------

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
 * Defines a [LexerParser].
 */
public open class LexerParserDefinition internal constructor() : ParserDefinition() {
    internal val recoveryDelegate = OnceAssignable<LexerComponent>(::MalformedParserException)

    /**
     * During [tokenization][LexerParser.tokenize], if a sequence of characters cannot be matched
     * to a named [LexerSymbol], any adjacent substrings matching this symbol that do not match a named lexer symbol
     * are combined into a single unnamed token.
     *
     * If left unspecified and a match cannot be made to a named lexer symbol, an [IllegalTokenException] is thrown.
     * This exception is also thrown if a match to this symbol produces a token of an empty substring
     * (e.g., if this is an [Option]).
     * @throws MalformedParserException this property is left unassigned, or is assigned a value more than once
     */
    public var recovery: LexerComponent by recoveryDelegate

    /**
     * The lexer symbols to be ignored during lexical analysis.
     *
     * Nodes produced by these symbols will not be present in the list returned by
     * [tokenize][LexerParser.tokenize].
     * @throws MalformedParserException this property is left unassigned, or is assigned a value more than once
     */
    public val skip: MutableList<NamedSymbol<LexerSymbol>> = mutableListOf()

    internal val lexerModes = mutableMapOf<String, MutableList<NamedSymbol<LexerSymbol>>>()

    private var mode = DEFAULT_MODE_NAME

    // ------------------------------ lexer mode configuration -------------------------

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
        mode = DEFAULT_MODE_NAME
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
    @Suppress("unused")
    public operator fun Fragment.provideDelegate(
        thisRef: Any?,
        symbol: KProperty<*>
    ): ReadOnlyProperty<Nothing?, NamedSymbol<LexerSymbol>> {
        return NamedSymbol(symbol.name, LexerSymbol(this)).toNamedProperty()
    }

    /**
     * Assigns a [LexerSymbol] matching this descriptor to the property being delegated to.
     */
    @Suppress("unused")
    public operator fun Descriptor.provideDelegate(
        thisRef: Any?,
        symbol: KProperty<*>
    ): ReadOnlyProperty<Nothing?, NamedSymbol<LexerSymbol>> {
        return NamedSymbol(symbol.name, LexerSymbol(fragment, behavior)).toNamedProperty()
    }

    // ------------------------------ text & switch factories ------------------------------

    /**
     * Assigns a [LexerSymbol] matching this character to the property being delegated to.
     */
    @Suppress("unused")
    public operator fun Char.provideDelegate(
        thisRef: Any?,
        symbol: KProperty<*>
    ): ReadOnlyProperty<Any?, NamedSymbol<LexerSymbol>> {
        val named = NamedSymbol(symbol.name, LexerSymbol(Fragment(Text(this))))
        lexerModes.getValue(mode) += named
        return named.toNamedProperty()
    }

    public override fun text(query: String): Fragment = Fragment(Text(query))
    public override fun of(switch: String): Fragment {
        return Fragment(super.of(switch) as Switch)
    }

    // ------------------------------ option factories ------------------------------

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

    // ------------------------------ repetition factories ------------------------------

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

    // ------------------------------ optional repetition factories ------------------------------

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

    // ------------------------------ junction factories ------------------------------

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

    // ------------------------------ sequence factories ------------------------------

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