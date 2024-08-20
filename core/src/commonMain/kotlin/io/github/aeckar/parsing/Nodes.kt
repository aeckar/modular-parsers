package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.JunctionNode
import io.github.aeckar.parsing.typesafe.TypeSafeJunction
import io.github.aeckar.parsing.utils.fragileUnsafeCast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.jvm.JvmName

// Functions with Token<...> receiver must be extensions to ensure proper nesting of token contexts in listeners

/**
 * Returns the concatenation of the substrings of all elements in this list.
 */
internal fun List<Node<*>>.concatenate() = joinToString("") { it.substring }

/**
 * A matched substring in a given input produced according to the matching logic of a symbol.
 *
 * When nodes are combined into a hierarchy, they form an [abstract syntax tree][Parser.parse].
 *
 * Because extension functions of this class are specific to the parameter [MatchT],
 * performing a cast to an instance with another type parameter may break the API.
 */
public open class Node<MatchT : Symbol> internal constructor(
    internal var source: MatchT,
    public val substring: String
) {
    /**
     * The children of this node as the root of an abstract syntax tree.
     */
    public open val children: List<Node<*>> = persistentListOf()    // Empty for leaf nodes

    /**
     * The name assigned to this symbol, or null if one does not exist.
     */
    public fun isNamed(): Boolean = source is NameableSymbol<*>

    /**
     * Returns the name assigned to this symbol if it exists, else its EBNF representation.
     */
    final override fun toString(): String = source.rawName
}

// ------------------------------ option nodes ------------------------------

/**
 * A token emitted by an [Option].
 */
@PublishedApi
internal class OptionNode<SubMatchT : Symbol> internal constructor(
    symbol: Option<SubMatchT>,
    substring: String,
    @PublishedApi internal val match: Node<SubMatchT>?
) : Node<Option<SubMatchT>>(symbol, substring)

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
public inline fun <SubMatchT : Symbol> Node<Option<SubMatchT>>.onSuccess(action: Node<SubMatchT>.() -> Unit) {
    fragileUnsafeCast<OptionNode<SubMatchT>>().match?.apply(action)
}

// ------------------------------ repetition nodes ------------------------------

/**
 * A token emitted by a [Repetition].
 */
internal class RepetitionNode<SubMatchT : Symbol> internal constructor(
    symbol: Repetition<SubMatchT>,
    substring: String,
    internal val branches: List<Node<SubMatchT>>
) : Node<Repetition<SubMatchT>>(symbol, substring) {
    override val children: List<Node<SubMatchT>> by lazy { branches.toImmutableList() }
}

// ------------------------------ junction nodes ------------------------------

/**
 * The index of the option matched by the symbol emitting this token.
 */
@get:JvmName("matchOrdinal\$ImplicitJunction")
public val Node<out ImplicitJunction<*>>.matchOrdinal: Int get() = fragileUnsafeCast<JunctionNode>().matchOrdinal

/**
 * The index of the option matched by the symbol emitting this token.
 */
@get:JvmName("matchOrdinal\$TypeSafeJunction")
public val Node<TypeSafeJunction<*>>.matchOrdinal: Int get() = fragileUnsafeCast<JunctionNode>().matchOrdinal