package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.JunctionNode
import io.github.aeckar.parsing.typesafe.TypeSafeJunction
import io.github.aeckar.parsing.utils.fragileUnsafeCast
import kotlinx.collections.immutable.persistentListOf

// Functions with Token<...> receiver must be extensions to ensure proper nesting of token contexts in listeners

/**
 * A matched substring in a given input produced according to the matching logic of a symbol.
 *
 * When nodes are combined into a hierarchy, they form an [abstract syntax tree][Parser.toAST].
 *
 * Because extension functions of this class are specific to the parameter [T],
 * performing a cast to an instance with another type parameter may break the API.
 */
public open class Node<T : Symbol> internal constructor(
    private val symbol: T,
    public val substring: String
) {
    /**
     * The children of this node as the root of an abstract syntax tree.
     */
    public open val children: List<Node<*>> = persistentListOf()

    /**
     * The name assigned to this symbol, or null if one does not exist.
     */
    public fun isNamed(): Boolean = symbol is NameableSymbol<*>

    /**
     * Returns the name assigned to this symbol if it exists, else its EBNF representation.
     */
    final override fun toString(): String = symbol.rawName
}

// ------------------------------ option nodes ------------------------------

/**
 * A token emitted by an [option symbol][Option].
 */
@PublishedApi
internal class OptionNode<T : Symbol> internal constructor(
    symbol: Option<T>,
    substring: String,
    @PublishedApi internal val match: Node<T>?
) : Node<Option<T>>(symbol, substring)

/**
 * Returns true if a match to a token was made.
 */
public fun Node<Option<*>>.matchSucceeded(): Boolean = fragileUnsafeCast<OptionNode<*>>().match != null

/**
 * Returns true if a match to a token was not made.
 */
public fun Node<Option<*>>.matchFailed(): Boolean = fragileUnsafeCast<OptionNode<*>>().match == null

/**
 * Performs the [action] using the matched token, [if present][matchSucceeded].
 * Otherwise, does nothing.
 */
public inline fun <T : Symbol> Node<Option<T>>.onSuccess(action: Node<T>.() -> Unit) {
    fragileUnsafeCast<OptionNode<T>>().match?.apply(action)
}

// ------------------------------ repetition nodes ------------------------------

/**
 * A token emitted by a [repetition symbol][Repetition].
 */
internal class RepetitionNode<T : Symbol> internal constructor(
    symbol: Repetition<T>,
    substring: String,
    override val children: List<Node<T>>
) : Node<Repetition<T>>(symbol, substring)

// ------------------------------ junction nodes ------------------------------

/**
 * The index of the option matched by the symbol emitting this token.
 */
public val Node<Junction<*>>.matchOrdinal: Int get() = fragileUnsafeCast<JunctionNode>().matchOrdinal

/**
 * The index of the option matched by the symbol emitting this token.
 */
public val Node<TypeSafeJunction<*>>.matchOrdinal: Int get() = fragileUnsafeCast<JunctionNode>().matchOrdinal