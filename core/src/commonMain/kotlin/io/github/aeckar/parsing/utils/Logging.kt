package io.github.aeckar.parsing.utils

import io.github.aeckar.parsing.SyntaxTreeNode
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

private fun String.parenthesize() = takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""

/**
 * Prepends this object to the debug message.
 */
internal fun Any?.debug(lazyMessage: () -> String) = logger.debug { "${lazyMessage()} @ $this" }

/**
 * Prepends this object to the debug message.
 */
internal fun Any?.debugMatchFail(lazyReason: () -> String = { "" }) {
    logger.debug { "Match failed${lazyReason().parenthesize()} @ $this" }
}

/**
 * Prepends this object to the debug message.
 */
internal fun Any?.debugMatchSuccess(result: SyntaxTreeNode<*>, lazyReason: () -> String = { "" }) {
    logger.debug {
        "Match succeeded${lazyReason().parenthesize()} (substring = '${result.substring.withEscapes()}') @ $this"
    }
}