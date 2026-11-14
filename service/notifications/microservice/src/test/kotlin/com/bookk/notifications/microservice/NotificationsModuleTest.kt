package com.bookk.notifications.microservice

import com.bookk.core.data.eventstreaming.ExhaustedEventReplayer
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.impl.di.NotificationsScope
import library.email.EmailSender
import library.encryption.impl.test.subjectKeyTestEnvironment
import library.outbox.personal.PersonalDataCipher
import library.outbox.personal.SubjectKeysPersonalDataCipher
import library.outbox.test.serviceScopeWithMockedInfrastructure
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.core.scope.Scope

internal class NotificationsModuleTest {

    private class SutFixture(serviceModule: Module) {
        val scope: Scope = serviceScopeWithMockedInfrastructure(NotificationsScope, serviceModule)
    }

    @Test
    fun `should bind no event producer in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(notificationsModule(subjectKeyTestEnvironment::get))

        whenn()
        val producer = fixture.scope.getOrNull<StandardEventProducer>()

        then()
        assertNull(producer)
    }

    @Test
    fun `should bind no event producer in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(notificationsEmbeddedModule(subjectKeyTestEnvironment::get))

        whenn()
        val producer = fixture.scope.getOrNull<StandardEventProducer>()

        then()
        assertNull(producer)
    }

    @Test
    fun `should replay parked events in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(notificationsModule(subjectKeyTestEnvironment::get))

        whenn()
        val replayer = fixture.scope.getOrNull<ExhaustedEventReplayer>()

        then()
        assertNotNull(replayer)
    }

    @Test
    fun `should replay parked events in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(notificationsEmbeddedModule(subjectKeyTestEnvironment::get))

        whenn()
        val replayer = fixture.scope.getOrNull<ExhaustedEventReplayer>()

        then()
        assertNotNull(replayer)
    }

    private val developmentEmailEnvironment = subjectKeyTestEnvironment + mapOf(
        "APPLICATION_POSTMARK_SERVER_TOKEN" to "development_token",
        "APPLICATION_EMAIL_FROM" to "no-reply@example.com",
    )

    @Test
    fun `should wire email delivery in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(notificationsModule(developmentEmailEnvironment::get))

        whenn()
        val resolve = { fixture.scope.get<EmailSender>() }

        then()
        assertDoesNotThrow(resolve)
    }

    @Test
    fun `should wire email delivery in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(notificationsEmbeddedModule(developmentEmailEnvironment::get))

        whenn()
        val resolve = { fixture.scope.get<EmailSender>() }

        then()
        assertDoesNotThrow(resolve)
    }

    @Test
    fun `should encrypt the personal data of parked events with the service's subject keys in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(notificationsEmbeddedModule(subjectKeyTestEnvironment::get))

        whenn()
        val cipher = fixture.scope.get<PersonalDataCipher>()

        then()
        assertTrue(cipher is SubjectKeysPersonalDataCipher)
    }
}
