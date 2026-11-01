package com.bookk.core.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import kotlin.test.Test

internal class RequestObservabilityTest {

    private class SutFixture {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val appender = ListAppender<ILoggingEvent>()
        val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
    }

    @Test
    fun `should export request durations as histogram buckets`() = routeTest {
        given()
        val fixture = SutFixture()
        setupApplication(
            extension = { installRequestMetrics(fixture.registry) },
            diModule = module { },
            routeUnderTest = { get("/ping") { call.respond(HttpStatusCode.NoContent) } }
        )

        whenn()
        client.get("/ping")
        val scrape = fixture.registry.scrape()

        then()
        val pingBuckets = scrape.lines().filter { it.startsWith("ktor_http_server_requests_seconds_bucket{") && """route="/ping"""" in it }
        assertTrue(pingBuckets.any { """le="0.1"""" in it && it.endsWith(" 1") }) { scrape }
        assertTrue(pingBuckets.any { """le="+Inf"""" in it && it.endsWith(" 1") }) { scrape }
        assertFalse(scrape.contains("quantile=")) { scrape }
    }

    @Test
    fun `should scrape the meters services register through dependency injection`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val koin = koinApplication { modules(meterRegistryModule(fixture.registry)) }.koin

        whenn()
        MultiGauge.builder("events.exhausted").register(koin.get<MeterRegistry>())
            .register(listOf(MultiGauge.Row.of(Tags.of("consumer_group", "user_group", "topic", "auth.user_deleted"), 2)))
        val scrape = fixture.registry.scrape()

        then()
        assertTrue(scrape.lines().any { it == """events_exhausted{consumer_group="user_group",topic="auth.user_deleted"} 2.0""" }) { scrape }
    }

    @Test
    fun `should log every request at info`() = routeTest {
        given()
        val fixture = SutFixture()
        fixture.appender.start()
        fixture.rootLogger.addAppender(fixture.appender)
        setupApplication(
            extension = { installRequestLogging() },
            diModule = module { },
            routeUnderTest = { get("/ping") { call.respond(HttpStatusCode.NoContent) } }
        )

        whenn()
        try {
            client.get("/ping")
        } finally {
            fixture.rootLogger.detachAppender(fixture.appender)
        }

        then()
        assertTrue(fixture.appender.list.any { it.level == Level.INFO && it.formattedMessage == "[204 No Content], GET /ping" }) {
            fixture.appender.list.joinToString { "${it.level} ${it.formattedMessage}" }
        }
    }
}
