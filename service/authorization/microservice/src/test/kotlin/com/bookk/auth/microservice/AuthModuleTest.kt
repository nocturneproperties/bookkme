package com.bookk.auth.microservice

import com.bookk.auth.domain.api.delete_account.operation.DeleteAccount
import com.bookk.auth.domain.api.email.operation.ConfirmEmailChange
import com.bookk.auth.domain.api.email.operation.StartEmailChange
import com.bookk.auth.domain.api.registration.operation.FinishRegistration
import com.bookk.auth.domain.api.registration.operation.StartRegistration
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.core.data.eventstreaming.ExhaustedEventReplayer
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.mockk
import library.outbox.OutboxEventProducer
import library.outbox.OutboxRelay
import library.outbox.test.serviceScopeWithMockedInfrastructure
import library.signing.GetVerificationKeys
import library.signing.RotateSigningKeys
import library.signing.TokenIssuer
import library.signing.TokenValidatorFactory
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module

internal class AuthModuleTest {

    private class SutFixture(serviceModule: Module) {
        val scope: Scope = serviceScopeWithMockedInfrastructure(
            AuthScope,
            module {
                includes(serviceModule)
                single<TokenValidatorFactory> { mockk() }
            },
        )
    }

    @Test
    fun `should wire account deletion with action confirmation in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(authModule())

        whenn()
        val resolve = { fixture.scope.get<DeleteAccount>() }

        then()
        assertDoesNotThrow(resolve)
    }

    @Test
    fun `should wire account deletion with action confirmation in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(authEmbeddedModule())

        whenn()
        val resolve = { fixture.scope.get<DeleteAccount>() }

        then()
        assertDoesNotThrow(resolve)
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

    @Test
    fun `should replay parked events in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(authModule())

        whenn()
        val replayer = fixture.scope.getOrNull<ExhaustedEventReplayer>()

        then()
        assertNotNull(replayer)
    }

    @Test
    fun `should replay parked events in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(authEmbeddedModule())

        whenn()
        val replayer = fixture.scope.getOrNull<ExhaustedEventReplayer>()

        then()
        assertNotNull(replayer)
    }

    @Test
    fun `should wire signing keys in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(authModule())

        whenn()
        val resolve = {
            fixture.scope.get<TokenIssuer>()
            fixture.scope.get<GetVerificationKeys>()
            fixture.scope.get<RotateSigningKeys>()
        }

        then()
        assertDoesNotThrow(resolve)
    }

    @Test
    fun `should wire signing keys in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(authEmbeddedModule())

        whenn()
        val resolve = {
            fixture.scope.get<TokenIssuer>()
            fixture.scope.get<GetVerificationKeys>()
            fixture.scope.get<RotateSigningKeys>()
        }

        then()
        assertDoesNotThrow(resolve)
    }

    @Test
    fun `should wire sign-up and email change with email codes in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(authModule(EMAIL_ENVIRONMENT::get))

        whenn()
        val resolve = {
            fixture.scope.get<StartRegistration>()
            fixture.scope.get<FinishRegistration>()
            fixture.scope.get<StartEmailChange>()
            fixture.scope.get<ConfirmEmailChange>()
        }

        then()
        assertDoesNotThrow(resolve)
    }

    @Test
    fun `should wire sign-up and email change with email codes in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(authEmbeddedModule(EMAIL_ENVIRONMENT::get))

        whenn()
        val resolve = {
            fixture.scope.get<StartRegistration>()
            fixture.scope.get<FinishRegistration>()
            fixture.scope.get<StartEmailChange>()
            fixture.scope.get<ConfirmEmailChange>()
        }

        then()
        assertDoesNotThrow(resolve)
    }

    private companion object {
        val EMAIL_ENVIRONMENT = mapOf(
            "APPLICATION_POSTMARK_SERVER_TOKEN" to "postmark-token",
            "APPLICATION_EMAIL_FROM" to "noreply@example.com",
            "APPLICATION_EMAIL_CODE_SECRET" to "a-secret-that-is-at-least-32-bytes-long",
        )
    }
}
