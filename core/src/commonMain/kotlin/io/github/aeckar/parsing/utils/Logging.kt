package io.github.aeckar.parsing.utils

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

private val apiLogger = KotlinLogging.logger("modular-parsers")

/**
 * Sends the message to the top-level logger `modular-parsers`.
 *
 * Appends the receiver to the debug message.
 */
internal fun Any.debug(lazyMessage: () -> String) = apiLogger.debugAt(this, lazyMessage)

/**
 * Sends the message to the receiver.
 *
 * Appends [obj] to the debug message.
 */
internal fun KLogger.debugAt(obj: Any?, lazyMessage: () -> String) = debug { lazyMessage() + " @ $obj".grey() }