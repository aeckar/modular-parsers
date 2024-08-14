package io.github.aeckar.parsing.typesafe

import io.github.aeckar.parsing.*

/**
 * A type-safe symbol.
 *
 * Enables type-safe access to children of each [Node] produced by this symbol.
 */
public abstract class TypeSafeSymbol<
    TypeUnsafeT : TypeUnsafeSymbol<InheritorT, TypeUnsafeT>,
    InheritorT : TypeSafeSymbol<TypeUnsafeT, InheritorT>
> internal constructor(internal val untyped: TypeUnsafeT) : NameableSymbol<InheritorT>() {
    final override fun match(data: ParserMetadata): Node<*>? = untyped.match(data)  // Checked by parser beforehand

    final override fun resolveRawName() = untyped.rawName
}

/**
 * A type-safe [Junction] wrapper.
 */
public abstract class TypeSafeJunction<InheritorT : TypeSafeJunction<InheritorT>>
internal constructor(untyped: Junction<InheritorT>) : TypeSafeSymbol<Junction<InheritorT>, InheritorT>(untyped)

/**
 * A type-safe [Sequence] wrapper.
 */
public abstract class TypeSafeSequence<InheritorT : TypeSafeSequence<InheritorT>>
internal constructor(untyped: Sequence<InheritorT>) : TypeSafeSymbol<Sequence<InheritorT>, InheritorT>(untyped)