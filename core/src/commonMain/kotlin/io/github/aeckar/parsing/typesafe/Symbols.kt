package io.github.aeckar.parsing.typesafe

import io.github.aeckar.parsing.Lexer
import io.github.aeckar.parsing.NameableSymbol
import io.github.aeckar.parsing.Symbol

/**
 * A symbol whose matching logic is derived from that of another symbol.
 *
 * @param S the inheritor of this class
 */
public abstract class TypeSafeSymbol<S : TypeSafeSymbol<S>> internal constructor(
    private val untyped: Symbol
) : NameableSymbol<S>() {
    final override fun match(lexer: Lexer) = untyped.match(lexer)  // Checked by parser beforehand

    final override fun resolveRawName() = untyped.rawName
}

/**
 * A [junction symbol][io.github.aeckar.parsing.Junction] whose matching logic is derived from that of another symbol.
 *
 * @param S the inheritor of this class
 */
public abstract class TypeSafeJunction<S : TypeSafeJunction<S>> internal constructor(
    untyped: Symbol
) : TypeSafeSymbol<S>(untyped)

/**
 * A [sequence symbol][io.github.aeckar.parsing.Sequence] whose matching logic is derived from that of another symbol.
 *
 * @param S the inheritor of this class
 */
public abstract class TypeSafeSequence<S : TypeSafeSequence<S>> internal constructor(
    untyped: Symbol
) : TypeSafeSymbol<S>(untyped)
