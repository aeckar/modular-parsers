@file:Suppress("LeakingThis")
package io.github.aeckar.parsing.typesafe

import io.github.aeckar.parsing.*
import io.github.aeckar.parsing.utils.SymbolStream
import io.github.aeckar.parsing.utils.unsafeCast

/**
 * A symbol providing type-safe access to its components.
 *
 * Enables type-safe access to children of each [Node] produced by this symbol.
 */
public abstract class TypeSafeSymbol<
    TypeUnsafeT : TypeUnsafeSymbol<InheritorT, TypeUnsafeT>,
    InheritorT : TypeSafeSymbol<TypeUnsafeT, InheritorT>
> internal constructor(internal val untyped: TypeUnsafeT) : NameableSymbol<InheritorT>() {
    final override fun resolve() = untyped

    final override fun match(stream: SymbolStream): Node<*>? {
        return untyped.match(stream)?.also { it.unsafeCast<Node<Symbol>>().source = this }
    }

    final override fun resolveRawName() = untyped.rawName
}

/**
 * A type-safe [Junction] wrapper.
 */
public abstract class TypeSafeJunction<InheritorT : TypeSafeJunction<InheritorT>> internal constructor(
    untyped: Junction<InheritorT>
) : TypeSafeSymbol<Junction<InheritorT>, InheritorT>(untyped) {
    init {
        untyped.typed = this
    }
}

/**
 * A type-safe [Sequence] wrapper.
 */
public abstract class TypeSafeSequence<InheritorT : TypeSafeSequence<InheritorT>> internal constructor(
    untyped: Sequence<InheritorT>
) : TypeSafeSymbol<Sequence<InheritorT>, InheritorT>(untyped) {
    init {
        untyped.typed = this
    }
}