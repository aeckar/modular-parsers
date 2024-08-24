package io.github.aeckar.parsing

import io.github.oshai.kotlinlogging.KotlinLogging

private val symbolLogger = KotlinLogging.logger {}

/**
 * Prepends the [raw name][Symbol.rawName] of this symbol to the debug message.
 */
internal fun Symbol.debug(lazyMessage: () -> String) = symbolLogger.debug { "$rawName: ${lazyMessage()}" }

/**
 * Prepends the [raw name][Symbol.rawName] of this symbol to the debug message.
 */
internal fun Symbol.debugFail(lazyReason: () -> String = { "" }) {
    symbolLogger.debug {
        val reason = lazyReason().takeIf { it.isNotEmpty() }?.let { " ($it)" }
        "$rawName: Match failed$reason"
    }
}

/**
 * Prepends the [raw name][Symbol.rawName] of this symbol to the debug message.
 */
internal fun Symbol.debugSuccess(result: Node<*>) {
    symbolLogger.debug { "$rawName: Match succeeded (substring = ${result.substring})"}
}