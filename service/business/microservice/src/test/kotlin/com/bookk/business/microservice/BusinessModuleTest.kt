package com.bookk.business.microservice

import com.bookk.business.domain.api.client.operation.DeleteClient
import com.bookk.business.domain.api.export.DownloadBusinessDataExport
import com.bookk.business.domain.api.export.GetBusinessDataExport
import com.bookk.business.domain.api.export.StartBusinessDataExport
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.core.data.eventstreaming.ExhaustedEventReplayer
import com.bookk.core.data.eventstreaming.data.test.serviceScopeWithMockedInfrastructure
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.mockk
import library.export.BuildDataExports
import library.export.DeleteExpiredDataExports
import library.signing.TokenValidatorFactory
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module

internal class BusinessModuleTest {

    private class SutFixture(serviceModule: Module) {
        val scope: Scope = serviceScopeWithMockedInfrastructure(
            BusinessScope,
            module {
                includes(serviceModule)
                single<TokenValidatorFactory> { mockk() }
            },
        )
    }

    @Test
    fun `should replay parked events in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(businessModule())

        whenn()
        val replayer = fixture.scope.getOrNull<ExhaustedEventReplayer>()

        then()
        assertNotNull(replayer)
    }

    @Test
    fun `should replay parked events in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(businessEmbeddedModule())

        whenn()
        val replayer = fixture.scope.getOrNull<ExhaustedEventReplayer>()

        then()
        assertNotNull(replayer)
    }

    @Test
    fun `should guard client deletion with a step-up gate in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(businessModule())

        whenn()
        val resolve = { fixture.scope.get<DeleteClient>() }

        then()
        assertDoesNotThrow(resolve)
    }

    @Test
    fun `should guard client deletion with a step-up gate in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(businessEmbeddedModule())

        whenn()
        val resolve = { fixture.scope.get<DeleteClient>() }

        then()
        assertDoesNotThrow(resolve)
    }

    @Test
    fun `should wire the business data export in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(businessModule())

        whenn()
        val resolve = {
            fixture.scope.get<StartBusinessDataExport>()
            fixture.scope.get<GetBusinessDataExport>()
            fixture.scope.get<DownloadBusinessDataExport>()
            fixture.scope.get<BuildDataExports>()
            fixture.scope.get<DeleteExpiredDataExports>()
        }

        then()
        assertDoesNotThrow(resolve)
    }

    @Test
    fun `should wire the business data export in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(businessEmbeddedModule())

        whenn()
        val resolve = {
            fixture.scope.get<StartBusinessDataExport>()
            fixture.scope.get<BuildDataExports>()
        }

        then()
        assertDoesNotThrow(resolve)
    }
}
