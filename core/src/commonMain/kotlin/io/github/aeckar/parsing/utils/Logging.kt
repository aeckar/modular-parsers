package io.github.aeckar.parsing.utils

import io.github.aeckar.parsing.SyntaxTreeNode
import io.github.oshai.kotlinlogging.*

private val logger = object : KLogger {
    override val name: String
        get() = ""

    override fun at(level: Level, marker: Marker?, block: KLoggingEventBuilder.() -> Unit) {
        println(KLoggingEventBuilder().apply(block).message)
    }

    override fun isLoggingEnabledFor(level: Level, marker: Marker?): Boolean {
        return true
    }

}

/**
 * Prepends this object to the debug message.
 */
internal fun Any?.debug(lazyMessage: () -> String) = logger.debug { "${lazyMessage()} @ $this" }

/**
 * Prepends this object to the debug message.
 */
internal fun Any?.debugMatchFail(lazyReason: () -> String = { "" }) {
    logger.debug {
        val reason = lazyReason().takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""
        "Match failed$reason @ $this"
    }
}

/**
 * Prepends this object to the debug message.
 */
internal fun Any?.debugMatchSuccess(result: SyntaxTreeNode<*>) {
    logger.debug { "Match succeeded (substring = ${result.substring.withEscapes()}) @ $this"}
}