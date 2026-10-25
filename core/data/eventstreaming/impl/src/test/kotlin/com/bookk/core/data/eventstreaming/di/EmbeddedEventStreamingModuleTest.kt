package com.bookk.core.data.eventstreaming.di

import com.bookk.core.data.eventstreaming.EventIdempotencyStorage
import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.StandardEventConsumer
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.impl.InMemoryExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.embedded.EmbeddedEventConsumer
import com.bookk.core.data.eventstreaming.impl.embedded.EmbeddedEventProducer
import com.bookk.core.data.eventstreaming.impl.embedded.publishEncoded
import com.bookk.core.data.eventstreaming.impl.inMemoryEventIdempotencyStorage
import com.bookk.core.data.eventstreaming.impl.kafka.KeyedTestEvent
import com.bookk.core.data.eventstreaming.registerReceiver
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.util.concurrent.ConcurrentLinkedQueue

internal class EmbeddedEventStreamingModuleTest {

    private class SutFixture {
        val qualifier = named("embedded-test")
        private val sharedModule = module {
            single<EventIdempotencyStorage> { inMemoryEventIdempotencyStorage() }
        }

        private fun serviceModule(qualifier: Qualifier) = module {
            scope(qualifier) { scoped<ExhaustedEventDataSource> { InMemoryExhaustedEventDataSource() } }
        }

        fun scopeWith(eventStreaming: Module): Scope =
            koinApplication { modules(topicQueueHolderModule(), sharedModule, serviceModule(qualifier), eventStreaming) }
                .koin
                .createScope("embedded-test-scope", qualifier)

        fun scopesOf(vararg services: Pair<Qualifier, String>): List<Scope> {
            val koin = koinApplication {
                modules(listOf(topicQueueHolderModule(), sharedModule) + services.flatMap { (qualifier, serviceName) ->
                    listOf(serviceModule(qualifier), embeddedEventStreamingTransportModule(qualifier, serviceName))
                })
            }.koin
            return services.map { (qualifier, serviceName) -> koin.createScope("$serviceName-scope", qualifier) }
        }
    }

    @Test
    fun `should provide the embedded transport as the raw producer and consumer`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val scope = fixture.scopeWith(embeddedEventStreamingTransportModule(fixture.qualifier, "embedded-test"))

        whenn()
        val rawProducer = scope.get<EventStreaming.RawProducer<String>>()
        val consumer = scope.get<StandardEventConsumer>()

        then()
        assertTrue(rawProducer is EmbeddedEventProducer)
        assertTrue(consumer is EmbeddedEventConsumer)
    }

    @Test
    fun `should leave the event producer to the outbox when only the transport is included`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val scope = fixture.scopeWith(embeddedEventStreamingTransportModule(fixture.qualifier, "embedded-test"))

        whenn()
        val producer = scope.getOrNull<StandardEventProducer>()

        then()
        assertNull(producer)
    }

    @Test
    fun `should let every service in one process handle the same event once`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "embedded.module.fan-out"
        val (userScope, businessScope) = fixture.scopesOf(named("user") to "user", named("business") to "business")
        val userReceived = ConcurrentLinkedQueue<KeyedTestEvent>()
        val businessReceived = ConcurrentLinkedQueue<KeyedTestEvent>()
        userScope.get<StandardEventConsumer>()
            .registerReceiver<KeyedTestEvent, String>(topic) { event -> userReceived += event }
            .start(backgroundScope)
        businessScope.get<StandardEventConsumer>()
            .registerReceiver<KeyedTestEvent, String>(topic) { event -> businessReceived += event }
            .start(backgroundScope)

        val event = KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic)

        whenn()
        userScope.get<EventStreaming.RawProducer<String>>().publishEncoded(event)
        runCurrent()

        then()
        assertEquals(1, userReceived.size)
        assertEquals(1, businessReceived.size)
    }
}
