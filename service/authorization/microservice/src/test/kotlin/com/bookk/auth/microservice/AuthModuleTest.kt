package com.bookk.auth.microservice

import com.bookk.auth.domain.impl.di.AuthScope
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal class AuthModuleTest {

    private class SutFixture(serviceModule: Module) {
        private val infrastructure = module {
            scope(AuthScope) {
                scoped<TransactionManager> { mockk() }
                scoped<EventStreaming.RawProducer<String>> { mockk() }
            }
        }
        val scope: Scope = koinApplication { modules(serviceModule, infrastructure) }
            .koin
            .createScope("auth-module-test", AuthScope)
    }

    @Test
    fun `should send events through the outbox in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(authModule())

        whenn()
        val producer = fixture.scope.get<StandardEventProducer>()

        then()
        assertTrue(producer is OutboxEventProducer)
    }

    @Test
    fun `should start the outbox relay with event handling in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(authModule())

        whenn()
        val handlers = fixture.scope.getAll<EventHandler>()

        then()
        assertTrue(handlers.any { it is OutboxRelay })
    }

    @Test
    fun `should send events through the outbox in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(authEmbeddedModule())

        whenn()
        val producer = fixture.scope.get<StandardEventProducer>()

        then()
        assertTrue(producer is OutboxEventProducer)
    }

    @Test
    fun `should start the outbox relay with event handling in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(authEmbeddedModule())

        whenn()
        val handlers = fixture.scope.getAll<EventHandler>()

        then()
        assertTrue(handlers.any { it is OutboxRelay })
    }
}
