package com.bookk.notifications.microservice

import com.bookk.core.data.eventstreaming.ExhaustedEventReplayer
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.data.test.serviceScopeWithMockedInfrastructure
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.impl.di.NotificationsScope
import library.email.EmailSender
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
        val fixture = SutFixture(notificationsModule())

        whenn()
        val producer = fixture.scope.getOrNull<StandardEventProducer>()

        then()
        assertNull(producer)
    }

    @Test
    fun `should bind no event producer in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(notificationsEmbeddedModule())

        whenn()
        val producer = fixture.scope.getOrNull<StandardEventProducer>()

        then()
        assertNull(producer)
    }

    @Test
    fun `should replay parked events in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(notificationsModule())

        whenn()
        val replayer = fixture.scope.getOrNull<ExhaustedEventReplayer>()

        then()
        assertNotNull(replayer)
    }

    @Test
    fun `should replay parked events in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(notificationsEmbeddedModule())

        whenn()
        val replayer = fixture.scope.getOrNull<ExhaustedEventReplayer>()

        then()
        assertNotNull(replayer)
    }

    private val developmentEmailEnvironment = mapOf(
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
}
