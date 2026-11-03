package com.bookk.auth.microservice.route.api.internal

import com.bookk.core.domain.entity.Error
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.export.AccountDataExport
import com.bookk.server.auth.client.export.ExportAccountData
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class ExportAccountDataTest {

    private val userId = Uuid.random()

    @Test
    fun `should return the account's export`() = routeTest {
        given()
        val useCase: ExportAccountData = mockk()
        val export = AccountDataExport.stub(passkeys = listOf(AccountDataExport.PasskeyExport.stub()))
        coEvery { useCase.invoke(userId) } returns Result.success(export)
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { exportAccountData() })

        whenn()
        val response = createTestClient().get("/api/internal/export/auth/$userId")

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(export, response.body<AccountDataExport>())
    }

    @Test
    fun `should answer 404 for a user without an account`() = routeTest {
        given()
        val useCase: ExportAccountData = mockk()
        coEvery { useCase.invoke(userId) } returns Result.failure(Error.NotFound())
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { exportAccountData() })

        whenn()
        val response = createTestClient().get("/api/internal/export/auth/$userId")

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
