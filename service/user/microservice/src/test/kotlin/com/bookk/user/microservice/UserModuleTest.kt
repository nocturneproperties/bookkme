package com.bookk.user.microservice

import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.core.data.eventstreaming.ExhaustedEventReplayer
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.operation.DownloadPersonalDataExport
import com.bookk.user.domain.api.operation.GetPersonalDataExport
import com.bookk.user.domain.api.operation.StartPersonalDataExport
import com.bookk.user.domain.impl.di.UserScope
import io.mockk.mockk
import library.export.BuildDataExports
import library.export.DeleteExpiredDataExports
import library.outbox.OutboxEventProducer
import library.outbox.OutboxRelay
import library.outbox.test.serviceScopeWithMockedInfrastructure
import library.signing.TokenValidatorFactory
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module
import kotlin.io.encoding.Base64

internal class UserModuleTest {

    private class SutFixture(serviceModule: Module) {
        val scope: Scope = serviceScopeWithMockedInfrastructure(
            UserScope,
            module {
                includes(serviceModule)
                single<TokenValidatorFactory> { mockk() }
            },
        )
    }

    @Test
    fun `should send events through the outbox in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(userModule(KEY_ENVIRONMENT::get))

        whenn()
        val producer = fixture.scope.get<StandardEventProducer>()

        then()
        assertTrue(producer is OutboxEventProducer)
    }

    @Test
    fun `should start the outbox relay with event handling in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(userModule(KEY_ENVIRONMENT::get))

        whenn()
        val handlers = fixture.scope.getAll<EventHandler>()

        then()
        assertTrue(handlers.any { it is OutboxRelay })
    }

    @Test
    fun `should send events through the outbox in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(userEmbeddedModule(KEY_ENVIRONMENT::get))

        whenn()
        val producer = fixture.scope.get<StandardEventProducer>()

        then()
        assertTrue(producer is OutboxEventProducer)
    }

    @Test
    fun `should start the outbox relay with event handling in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(userEmbeddedModule(KEY_ENVIRONMENT::get))

        whenn()
        val handlers = fixture.scope.getAll<EventHandler>()

        then()
        assertTrue(handlers.any { it is OutboxRelay })
    }

    @Test
    fun `should replay parked events in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(userModule(KEY_ENVIRONMENT::get))

        whenn()
        val replayer = fixture.scope.getOrNull<ExhaustedEventReplayer>()

        then()
        assertNotNull(replayer)
    }

    @Test
    fun `should replay parked events in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(userEmbeddedModule(KEY_ENVIRONMENT::get))

        whenn()
        val replayer = fixture.scope.getOrNull<ExhaustedEventReplayer>()

        then()
        assertNotNull(replayer)
    }

    @Test
    fun `should wire the personal data export in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(userModule(KEY_ENVIRONMENT::get))

        whenn()
        val resolve = {
            fixture.scope.get<StartPersonalDataExport>()
            fixture.scope.get<GetPersonalDataExport>()
            fixture.scope.get<DownloadPersonalDataExport>()
            fixture.scope.get<BuildDataExports>()
            fixture.scope.get<DeleteExpiredDataExports>()
        }

        then()
        assertDoesNotThrow(resolve)
    }

    @Test
    fun `should wire the personal data export in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(userEmbeddedModule(KEY_ENVIRONMENT::get))

        whenn()
        val resolve = {
            fixture.scope.get<StartPersonalDataExport>()
            fixture.scope.get<BuildDataExports>()
        }

        then()
        assertDoesNotThrow(resolve)
    }

    private companion object {
        val KEY_ENVIRONMENT = mapOf(
            "APPLICATION_SUBJECT_MASTER_KEY" to Base64.encode(ByteArray(32)),
            "APPLICATION_BLIND_INDEX_KEY" to Base64.encode(ByteArray(32)),
        )
    }
}
