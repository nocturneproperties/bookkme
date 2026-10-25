package com.bookk.core.client

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

internal class InternalCallLoggingTest {

    private class SutFixture {
        val appender = ListAppender<ILoggingEvent>()
        val logger = LoggerFactory.getLogger(INTERNAL_CALL_LOGGER) as Logger
        val sut = HttpClient(MockEngine { respond("stored-response", HttpStatusCode.OK) }) {
            installInternalCallLogging()
        }
    }

    @Test
    fun `should log internal calls with their bodies at debug only`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val previousLevel = fixture.logger.level
        fixture.logger.level = Level.DEBUG
        fixture.appender.start()
        fixture.logger.addAppender(fixture.appender)

        whenn()
        try {
            fixture.sut.post("http://business.service/api/business/internal/context") { setBody("sent-request") }
        } finally {
            fixture.logger.detachAppender(fixture.appender)
            fixture.logger.level = previousLevel
        }

        then()
        val events = fixture.appender.list
        val log = events.joinToString("\n") { it.formattedMessage }
        assertTrue(events.isNotEmpty()) { "nothing logged" }
        assertTrue(events.all { it.level == Level.DEBUG }) { events.joinToString { "${it.level} ${it.formattedMessage}" } }
        assertTrue("http://business.service/api/business/internal/context" in log) { log }
        assertTrue("sent-request" in log && "stored-response" in log) { log }
    }
}
