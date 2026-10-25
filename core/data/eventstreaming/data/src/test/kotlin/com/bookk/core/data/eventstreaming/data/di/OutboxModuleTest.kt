package com.bookk.core.data.eventstreaming.data.di

import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.data.outbox.OutboxEventProducer
import com.bookk.core.data.eventstreaming.data.outbox.OutboxRelay
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal class OutboxModuleTest {

    private class SutFixture {
        val qualifier = named("outbox-test")
        val transportModule = module {
            scope(qualifier) {
                scoped<TransactionManager> { mockk() }
                scoped<EventStreaming.RawProducer<String>> { mockk() }
            }
        }
        val scope = koinApplication { modules(transportModule, outboxModule(qualifier)) }
            .koin
            .createScope("outbox-test-scope", qualifier)
    }

    @Test
    fun `should make the outbox producer the scope's event producer`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val producer = fixture.scope.get<StandardEventProducer>()

        then()
        assertTrue(producer is OutboxEventProducer)
    }

    @Test
    fun `should register the relay as an event handler so event handling starts it`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val handlers = fixture.scope.getAll<EventHandler>()

        then()
        assertEquals(1, handlers.size)
        assertTrue(handlers.single() is OutboxRelay)
    }
}
