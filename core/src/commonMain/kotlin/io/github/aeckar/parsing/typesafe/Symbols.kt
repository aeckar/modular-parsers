@file:Suppress("LeakingThis")
package io.github.aeckar.parsing.typesafe

import io.github.aeckar.parsing.*
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
    final override fun unwrap() = untyped

    final override fun match(data: ParserMetadata): Node<*>? {
        return untyped.match(data)?.also { it.unsafeCast<Node<Symbol>>().source = this }
    }

    final override fun resolveRawName() = untyped.rawName
}

/**
 * A type-safe junction wrapper.
 */
public abstract class TypeSafeJunction<InheritorT : TypeSafeJunction<InheritorT>> internal constructor(
    untyped: TypeUnsafeJunction<InheritorT>
) : TypeSafeSymbol<TypeUnsafeJunction<InheritorT>, InheritorT>(untyped) {
    init {
        untyped.typed = this
    }
}

/**
 * A type-safe sequence wrapper.
 */
public abstract class TypeSafeSequence<InheritorT : TypeSafeSequence<InheritorT>> internal constructor(
    untyped: TypeUnsafeSequence<InheritorT>
) : TypeSafeSymbol<TypeUnsafeSequence<InheritorT>, InheritorT>(untyped) {
    init {
        untyped.typed = this
    }
}