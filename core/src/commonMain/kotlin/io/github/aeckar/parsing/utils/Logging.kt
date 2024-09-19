package io.github.aeckar.parsing.utils

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

internal val topLevel = KotlinLogging.logger("modular-parsers")

/**
 * Appends this object to the debug message.
 */
internal fun KLogger.debugAt(obj: Any?, lazyMessage: () -> String) = debug { lazyMessage() + " @ $obj".grey() }