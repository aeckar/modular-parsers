package io.github.aeckar.parsing.typesafe

import io.github.aeckar.parsing.*

/**
 * A type-safe symbol.
 *
 * Enables the todo
 *
 * @param U the untyped variant of this class
 * @param S the inheritor of this class
 */
public abstract class TypeSafeSymbol<U : ComplexSymbol<S, U>, S : TypeSafeSymbol<U, S>> internal constructor(
    internal val untyped: U
) : NameableSymbol<S>() {
    final override fun match(lexer: Lexer) = untyped.match(lexer)  // Checked by parser beforehand

    final override fun resolveRawName() = untyped.rawName
}

/**
 * A type-safe [junction symbol][io.github.aeckar.parsing.Junction].
 *
 * @param S the inheritor of this class
 */
public abstract class TypeSafeJunction<S : TypeSafeJunction<S>> internal constructor(
    untyped: Junction<S>
) : TypeSafeSymbol<Junction<S>, S>(untyped)

/**
 * A type-safe [sequence symbol][io.github.aeckar.parsing.Sequence].
 *
 * @param S the inheritor of this class
 */
public abstract class TypeSafeSequence<S : TypeSafeSequence<S>> internal constructor(
    untyped: Sequence<S>
) : TypeSafeSymbol<Sequence<S>, S>(untyped)
