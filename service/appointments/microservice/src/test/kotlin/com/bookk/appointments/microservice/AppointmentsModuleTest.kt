package com.bookk.appointments.microservice

import com.bookk.appointments.domain.api.operation.GetAppointment
import com.bookk.appointments.domain.impl.di.AppointmentsScope
import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.core.data.eventstreaming.ExhaustedEventReplayer
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.encryption.impl.test.subjectKeyTestEnvironment
import library.outbox.OutboxEventProducer
import library.outbox.OutboxRelay
import library.outbox.personal.PersonalDataCipher
import library.outbox.personal.SubjectKeysPersonalDataCipher
import library.outbox.test.serviceScopeWithMockedInfrastructure
import org.junit.jupiter.api.Assertions.assertNotNull
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
        val fixture = SutFixture(appointmentsModule(subjectKeyTestEnvironment::get))

        whenn()
        val producer = fixture.scope.get<StandardEventProducer>()

        then()
        assertTrue(producer is OutboxEventProducer)
    }

    @Test
    fun `should start the outbox relay with event handling in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(appointmentsModule(subjectKeyTestEnvironment::get))

        whenn()
        val handlers = fixture.scope.getAll<EventHandler>()

        then()
        assertTrue(handlers.any { it is OutboxRelay })
    }

    @Test
    fun `should send events through the outbox in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(appointmentsEmbeddedModule(subjectKeyTestEnvironment::get))

        whenn()
        val producer = fixture.scope.get<StandardEventProducer>()

        then()
        assertTrue(producer is OutboxEventProducer)
    }

    @Test
    fun `should start the outbox relay with event handling in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(appointmentsEmbeddedModule(subjectKeyTestEnvironment::get))

        whenn()
        val handlers = fixture.scope.getAll<EventHandler>()

        then()
        assertTrue(handlers.any { it is OutboxRelay })
    }

    @Test
    fun `should replay parked events in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(appointmentsModule(subjectKeyTestEnvironment::get))

        whenn()
        val replayer = fixture.scope.getOrNull<ExhaustedEventReplayer>()

        then()
        assertNotNull(replayer)
    }

    @Test
    fun `should replay parked events in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(appointmentsEmbeddedModule(subjectKeyTestEnvironment::get))

        whenn()
        val replayer = fixture.scope.getOrNull<ExhaustedEventReplayer>()

        then()
        assertNotNull(replayer)
    }

    @Test
    fun `should resolve get appointment in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(appointmentsModule(subjectKeyTestEnvironment::get))

        whenn()
        val operation = fixture.scope.get<GetAppointment>()

        then()
        assertNotNull(operation)
    }

    @Test
    fun `should resolve get appointment in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(appointmentsEmbeddedModule(subjectKeyTestEnvironment::get))

        whenn()
        val operation = fixture.scope.get<GetAppointment>()

        then()
        assertNotNull(operation)
    }

    @Test
    fun `should encrypt personal event data with the service's subject keys in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(appointmentsEmbeddedModule(subjectKeyTestEnvironment::get))

        whenn()
        val cipher = fixture.scope.get<PersonalDataCipher>()

        then()
        assertTrue(cipher is SubjectKeysPersonalDataCipher)
    }
}
