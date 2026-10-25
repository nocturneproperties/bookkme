package com.bookk.appointments.microservice

import com.bookk.appointments.domain.impl.di.AppointmentsScope
import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.data.outbox.OutboxEventProducer
import com.bookk.core.data.eventstreaming.data.outbox.OutboxRelay
import com.bookk.core.data.eventstreaming.data.test.serviceScopeWithMockedInfrastructure
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.core.scope.Scope

internal class AppointmentsModuleTest {

    private class SutFixture(serviceModule: Module) {
        val scope: Scope = serviceScopeWithMockedInfrastructure(AppointmentsScope, serviceModule)
    }

    @Test
    fun `should send events through the outbox in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(appointmentsModule())

        whenn()
        val producer = fixture.scope.get<StandardEventProducer>()

        then()
        assertTrue(producer is OutboxEventProducer)
    }

    @Test
    fun `should start the outbox relay with event handling in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(appointmentsModule())

        whenn()
        val handlers = fixture.scope.getAll<EventHandler>()

        then()
        assertTrue(handlers.any { it is OutboxRelay })
    }

    @Test
    fun `should send events through the outbox in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(appointmentsEmbeddedModule())

        whenn()
        val producer = fixture.scope.get<StandardEventProducer>()

        then()
        assertTrue(producer is OutboxEventProducer)
    }

    @Test
    fun `should start the outbox relay with event handling in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(appointmentsEmbeddedModule())

        whenn()
        val handlers = fixture.scope.getAll<EventHandler>()

        then()
        assertTrue(handlers.any { it is OutboxRelay })
    }
}
