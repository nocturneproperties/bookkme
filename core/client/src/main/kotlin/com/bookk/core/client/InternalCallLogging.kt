package com.bookk.core.client

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import org.slf4j.LoggerFactory

internal const val INTERNAL_CALL_LOGGER = "com.bookk.core.client.InternalCall"

private val internalCallLogger = LoggerFactory.getLogger(INTERNAL_CALL_LOGGER)

fun HttpClientConfig<*>.installInternalCallLogging() {
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) = internalCallLogger.debug(message)
        }
        level = LogLevel.BODY
    }
}
