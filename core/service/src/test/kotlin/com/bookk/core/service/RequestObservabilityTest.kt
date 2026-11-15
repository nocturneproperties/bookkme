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
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
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
            routeUnderTest = { get("/ping") { call.respond(HttpStatusCode.NoContent) } },
        )

        whenn()
        client.get("/ping")
        val scrape = fixture.registry.scrape()

        then()
        val pingBuckets = scrape.lines().filter {
            it.startsWith("ktor_http_server_requests_seconds_bucket{") &&
                """route="/ping"""" in it
        }
        assertTrue(pingBuckets.any { """le="0.1"""" in it && it.endsWith(" 1") }) { scrape }
        assertTrue(pingBuckets.any { """le="+Inf"""" in it && it.endsWith(" 1") }) { scrape }
        assertFalse(scrape.contains("quantile=")) { scrape }
    }

    @Test
    fun `should export response bytes under the route template`() = routeTest {
        given()
        val fixture = SutFixture()
        setupApplication(
            extension = { installResponseSizeMetrics(fixture.registry) },
            diModule = module { },
            routeUnderTest = { get("/things/{id}") { call.respondBytes(ByteArray(1500)) } },
        )

        whenn()
        client.get("/things/1")
        client.get("/things/2")
        val scrape = fixture.registry.scrape()

        then()
        assertTrue(
            scrape.lines().any {
                it == """http_server_response_size_bytes_sum{method="GET",route="/things/{id}"} 3000.0"""
            },
        ) { scrape }
    }

    @Test
    fun `should export response bytes sent before routing under one unrouted label`() = routeTest {
        given()
        val fixture = SutFixture()
        setupApplication(
            extension = {
                installResponseSizeMetrics(fixture.registry)
                intercept(ApplicationCallPipeline.Plugins) {
                    if (call.request.path().startsWith("/replayed/")) call.respondBytes(ByteArray(10))
                }
            },
            diModule = module { },
            routeUnderTest = { get("/things") { call.respond(HttpStatusCode.NoContent) } },
        )

        whenn()
        client.get("/replayed/1")
        client.get("/replayed/2")
        val scrape = fixture.registry.scrape()

        then()
        val responseSizeSums = scrape.lines().filter { it.startsWith("http_server_response_size_bytes_sum") }
        assertEquals(
            listOf("""http_server_response_size_bytes_sum{method="GET",route="unrouted"} 20.0"""),
            responseSizeSums,
        ) { scrape }
    }

    @Test
    fun `should export the bytes of a body written to the channel`() = routeTest {
        given()
        val fixture = SutFixture()
        setupApplication(
            extension = { installResponseSizeMetrics(fixture.registry) },
            diModule = module { },
            routeUnderTest = {
                get("/stream") {
                    call.respondBytesWriter { repeat(3) { writeFully(ByteArray(1000)) } }
                }
            },
        )

        whenn()
        val body = client.get("/stream").bodyAsBytes()
        val scrape = fixture.registry.scrape()

        then()
        assertEquals(3000, body.size)
        assertTrue(
            scrape.lines().any {
                it == """http_server_response_size_bytes_sum{method="GET",route="/stream"} 3000.0"""
            },
        ) { scrape }
    }

    @Test
    fun `should export the bytes of a body read from a channel`() = routeTest {
        given()
        val fixture = SutFixture()
        setupApplication(
            extension = { installResponseSizeMetrics(fixture.registry) },
            diModule = module { },
            routeUnderTest = {
                get("/channel") {
                    call.respond(
                        object : OutgoingContent.ReadChannelContent() {
                            override fun readFrom(): ByteReadChannel {
                                return ByteReadChannel(ByteArray(700))
                            }
                        },
                    )
                }
            },
        )

        whenn()
        val body = client.get("/channel").bodyAsBytes()
        val scrape = fixture.registry.scrape()

        then()
        assertEquals(700, body.size)
        assertTrue(
            scrape.lines().any {
                it == """http_server_response_size_bytes_sum{method="GET",route="/channel"} 700.0"""
            },
        ) { scrape }
    }

    @Test
    fun `should scrape the meters services register through dependency injection`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val koin = koinApplication { modules(meterRegistryModule(fixture.registry)) }.koin

        whenn()
        MultiGauge.builder("events.exhausted").register(koin.get<MeterRegistry>())
            .register(
                listOf(MultiGauge.Row.of(Tags.of("consumer_group", "user_group", "topic", "auth.user_deleted"), 2)),
            )
        val scrape = fixture.registry.scrape()

        then()
        assertTrue(
            scrape.lines().any {
                it ==
                    """events_exhausted{consumer_group="user_group",topic="auth.user_deleted"} 2.0"""
            },
        ) { scrape }
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
            routeUnderTest = { get("/ping") { call.respond(HttpStatusCode.NoContent) } },
        )

        whenn()
        try {
            client.get("/ping")
        } finally {
            fixture.rootLogger.detachAppender(fixture.appender)
        }

        then()
        assertTrue(
            fixture.appender.list.any {
                it.level == Level.INFO &&
                    it.formattedMessage == "[204 No Content], GET /ping"
            },
        ) {
            fixture.appender.list.joinToString { "${it.level} ${it.formattedMessage}" }
        }
    }
}
