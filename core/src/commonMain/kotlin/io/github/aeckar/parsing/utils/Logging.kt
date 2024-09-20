package io.github.aeckar.parsing.utils

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

private val apiLogger = KotlinLogging.logger("modular-parsers")

/**
 * Appends the receiver to the debug message.
 *
 * Sends the message to the top-level logger "modular-parsers".
 */
internal fun Any.debug(lazyMessage: () -> String) = apiLogger.debugAt(this, lazyMessage)

/**
 * Appends [obj] to the debug message.
 */
internal fun KLogger.debugAt(obj: Any?, lazyMessage: () -> String) = debug { lazyMessage() + " @ $obj".grey() }