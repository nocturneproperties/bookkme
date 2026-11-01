package com.bookk.core

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class LogbackConfigurationTest {

    private class SutFixture {
        val context = LoggerContext()
        val rootLogger: Logger get() = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        val relyingPartyLogger: Logger get() = context.getLogger("com.yubico.webauthn.RelyingParty")
        val logged = ListAppender<ILoggingEvent>()

        fun captureLogs() {
            logged.context = context
            logged.start()
            rootLogger.addAppender(logged)
        }

        fun configure() {
            val configurator = JoranConfigurator()
            configurator.context = context
            configurator.doConfigure(requireNotNull(javaClass.getResource("/logback.xml")))
        }
    }

    @Test
    fun `should log at info when no level is configured`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.configure()

        then()
        assertEquals(Level.INFO, fixture.rootLogger.level)
    }

    @Test
    fun `should log at the configured level`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.context.putProperty("APPLICATION_LOG_LEVEL", "debug")

        whenn()
        fixture.configure()

        then()
        assertEquals(Level.DEBUG, fixture.rootLogger.level)
    }

    @Test
    fun `should drop the relying party warning about non-url origins`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.configure()
        fixture.captureLogs()

        whenn()
        fixture.relyingPartyLogger.warn(
            "Allowed origin is not a valid URL, it will match only by exact string equality: {}",
            "android:apk-key-hash:l63z2UoYmCmoJ2V8Z9CfAxPkbhd7IjZvUrihusXdt10"
        )

        then()
        assertTrue(fixture.logged.list.isEmpty())
    }

    @Test
    fun `should keep other relying party warnings`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.configure()
        fixture.captureLogs()

        whenn()
        fixture.relyingPartyLogger.warn("Unknown algorithm: {}. Please file a bug report.", -7)

        then()
        assertEquals(1, fixture.logged.list.size)
    }
}
