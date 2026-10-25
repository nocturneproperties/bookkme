package com.bookk.core

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlin.test.Test
import kotlin.test.assertEquals

internal class LogbackConfigurationTest {

    private class SutFixture {
        val context = LoggerContext()
        val rootLogger: Logger get() = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)

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
}
