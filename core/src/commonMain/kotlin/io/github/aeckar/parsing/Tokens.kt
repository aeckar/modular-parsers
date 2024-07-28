package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.JunctionToken
import io.github.aeckar.parsing.typesafe.TypeSafeJunction
import io.github.aeckar.parsing.utils.ImmutableList
import io.github.aeckar.parsing.utils.fragileUnsafeCast

// Extensions of type Token<...> must be used to ensure proper nesting of token contexts in listeners

// ------------------------------ text & switch tokens ------------------------------

/**
 * A matched substring in a given input produced according to the matching logic of a symbol.
 *
 * When tokens are combined into a hierarchy, they form an [abstract syntax tree][todo].
 *
 * Because extension functions of this class are specific to the parameter [T],
 * performing a cast to an instance with another type parameter is discouraged.
 */
public open class Token<T : Symbol> internal constructor(
    private val symbol: T,
    public val substring: String
) {
    public val name: String? get() = symbol.takeIf { it is NamedSymbol<*> }?.let { it as Named }?.name
    public val rawName: String get() = symbol.rawName
}

// ------------------------------ option tokens ------------------------------

/**
 * A token emitted by an [option symbol][Option].
 */
@PublishedApi
internal class OptionToken<T : Symbol> internal constructor(
    symbol: Option<T>,
    substring: String,
    @PublishedApi internal val match: Token<T>?
) : Token<Option<T>>(symbol, substring)

/**
 * Returns true if a match to a token was made.
 */
public fun Token<Option<*>>.matchSucceeded(): Boolean = fragileUnsafeCast<OptionToken<*>>().match != null

/**
 * Returns true if a match to a token was not made.
 */
public fun Token<Option<*>>.matchFailed(): Boolean = fragileUnsafeCast<OptionToken<*>>().match == null

/**
 * Performs the [action] using the matched token, [if present][matchSucceeded].
 * Otherwise, does nothing.
 */
public inline fun <T : Symbol> Token<Option<T>>.onSuccess(action: Token<T>.() -> Unit) {
    fragileUnsafeCast<OptionToken<T>>().match?.apply(action)
}

// ------------------------------ repetition tokens ------------------------------

/**
 * A token emitted by a [repetition symbol][Repetition].
 */
internal class RepetitionToken<T : Symbol> internal constructor(
    symbol: Repetition<T>,
    substring: String,
    internal val matches: ImmutableList<Token<T>>
) : Token<Repetition<T>>(symbol, substring)

/**
 * The tokens matched by the symbol that emitted this one.
 */
public val <T : Symbol> Token<Repetition<T>>.matches: List<Token<T>>
    get() = fragileUnsafeCast<RepetitionToken<T>>().matches

// ------------------------------ junction tokens ------------------------------

public val Token<TypeSafeJunction<*>>.matchOrdinal: Int get() = fragileUnsafeCast<JunctionToken>().matchOrdinal