@file:JvmName("Nodes")
package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.JunctionNode
import io.github.aeckar.parsing.typesafe.TypeSafeJunction
import io.github.aeckar.parsing.containers.TreeNode
import io.github.aeckar.parsing.utils.unsafeCast
import kotlinx.collections.immutable.toImmutableList
import kotlin.jvm.JvmName

// Functions operating on AST nodes must be extensions to ensure proper nesting of token contexts in listeners

// TODO add functions to work with named symbol AST nodes

/**
 * A matched substring in a given input produced according to the matching logic of a symbol.
 *
 * When nodes are combined into a hierarchy, they form an [abstract syntax tree][Parser.parse].
 *
 * Because extension functions of this class are specific to the parameter [MatchT],
 * performing a cast to an instance with another type parameter may break the API.
 */
@ListenerDsl
public open class SyntaxTreeNode<MatchT : Symbol> internal constructor(
    internal var source: MatchT,

    /**
     * The substring matched by the symbol that produced this node.
     */
    public val substring: String
) : TreeNode<SyntaxTreeNode<*>> {
    /**
     * The name assigned to this symbol, or null if one does not exist.
     */
    public fun isNamed(): Boolean = source is NameableSymbol<*>

    /**
     * Returns the name assigned to the symbol that produced this node if it exists,
     * else the symbol's EBNF representation.
     */
    final override fun toString(): String = "$source @ '$substring'"
}

// ------------------------------ option nodes ------------------------------

/**
 * A token emitted by an [Option].
 */
@PublishedApi
internal class OptionNode<SubMatchT : Symbol> internal constructor(
    symbol: Option<SubMatchT>,
    substring: String,
    @PublishedApi internal val match: SyntaxTreeNode<SubMatchT>?
) : SyntaxTreeNode<Option<SubMatchT>>(symbol, substring)

/**
 * Returns true if a match to a token was made.
 */
public fun SyntaxTreeNode<Option<*>>.matchSucceeded(): Boolean = unsafeCast<OptionNode<*>>().match != null

/**
 * Returns true if a match to a token was not made.
 */
public fun SyntaxTreeNode<Option<*>>.matchFailed(): Boolean = unsafeCast<OptionNode<*>>().match == null

/**
 * Performs the [action] using the matched token, [if present][matchSucceeded].
 * Otherwise, does nothing.
 */
public inline fun <SubMatchT : Symbol> SyntaxTreeNode<Option<SubMatchT>>.onSuccess(
    action: SyntaxTreeNode<SubMatchT>.() -> Unit
) {
    unsafeCast<OptionNode<SubMatchT>>().match?.apply(action)
}

// ------------------------------ repetition nodes ------------------------------

/**
 * A token emitted by a [Repetition].
 */
internal class RepetitionNode<SubMatchT : Symbol> internal constructor(
    symbol: Repetition<SubMatchT>,
    substring: String,
    internal val branches: List<SyntaxTreeNode<SubMatchT>>
) : SyntaxTreeNode<Repetition<SubMatchT>>(symbol, substring) {
    override val children: List<SyntaxTreeNode<SubMatchT>> by lazy { branches.toImmutableList() }
}

// ------------------------------ junction nodes ------------------------------

/**
 * The index of the option matched by the symbol emitting this token.
 */
public val SyntaxTreeNode<TypeUnsafeJunction<*>>.matchOrdinal: Int get() = unsafeCast<JunctionNode>().matchOrdinal

/**
 * The index of the option matched by the symbol emitting this token.
 */
@get:JvmName("typeSafeMatchOrdinal")
public val SyntaxTreeNode<out TypeSafeJunction<*>>.matchOrdinal: Int get() = unsafeCast<JunctionNode>().matchOrdinal