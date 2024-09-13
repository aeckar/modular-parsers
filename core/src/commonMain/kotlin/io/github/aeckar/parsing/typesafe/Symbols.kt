package io.github.aeckar.parsing.typesafe

import io.github.aeckar.parsing.*
import io.github.aeckar.parsing.utils.self
import io.github.aeckar.parsing.utils.unsafeCast

/**
 * A symbol providing type-safe access to its components.
 *
 * Enables type-safe access to children of each [SyntaxTreeNode] produced by this symbol.
 */
public abstract class TypeSafeSymbol<
    TypeUnsafeT : TypeUnsafeSymbol<Self, TypeUnsafeT>,
    Self : TypeSafeSymbol<TypeUnsafeT, Self>
> internal constructor(internal val typeUnsafe: TypeUnsafeT) : NameableSymbol<Self>() {
    final override fun unwrap() = typeUnsafe

    final override fun match(attempt: ParsingAttempt): SyntaxTreeNode<*>? {
        return typeUnsafe.match(attempt)?.also { it.unsafeCast<SyntaxTreeNode<Symbol>>().source = this }
    }

    final override fun matchNoCache(attempt: ParsingAttempt) = match(attempt)
    final override fun resolveString() = typeUnsafe.toString()
}

/**
 * A type-safe junction wrapper.
 */
public abstract class TypeSafeJunction<Self : TypeSafeJunction<Self>> internal constructor(
    untyped: TypeUnsafeJunction<Self>
) : TypeSafeSymbol<TypeUnsafeJunction<Self>, Self>(untyped) {
    init {
        untyped.typeSafe = self()
    }
}

/**
 * A type-safe sequence wrapper.
 */
public abstract class TypeSafeSequence<Self : TypeSafeSequence<Self>> internal constructor(
    untyped: TypeUnsafeSequence<Self>
) : TypeSafeSymbol<TypeUnsafeSequence<Self>, Self>(untyped) {
    init {
        untyped.typeSafe = self()
    }
}