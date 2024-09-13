package io.github.aeckar.parsing.utils

import io.github.oshai.kotlinlogging.KotlinLogging

// For multithreaded applications, thread info can be dealt with by underlying logger
private val logger = KotlinLogging.logger("modular-parsers")

/**
 * Appends this object to the debug message.
 */
internal fun Any.debug(lazyMessage: () -> String) = logger.debug { lazyMessage() + " @ $this".grey() }