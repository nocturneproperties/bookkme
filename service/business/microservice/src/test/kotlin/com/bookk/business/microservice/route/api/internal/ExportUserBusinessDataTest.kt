package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.export.BusinessUserDataExport
import com.bookk.business.domain.api.export.ExportUserBusinessData
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class ExportUserBusinessDataTest {

    private val userId = Uuid.random()

    @Test
    fun `should return the user's business data`() = routeTest {
        given()
        val useCase: ExportUserBusinessData = mockk()
        val export = BusinessUserDataExport.stub(employments = listOf(BusinessUserDataExport.EmploymentExport.stub()))
        coEvery { useCase.invoke(userId) } returns Result.success(export)
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { exportUserBusinessData() })

        whenn()
        val response = createTestClient().get("/api/internal/export/business/$userId")

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(export, response.body<BusinessUserDataExport>())
    }

    @Test
    fun `should answer 500 when the data can't be read`() = routeTest {
        given()
        val useCase: ExportUserBusinessData = mockk()
        coEvery { useCase.invoke(userId) } returns Result.failure(IllegalStateException("database unreachable"))
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { exportUserBusinessData() })

        whenn()
        val response = createTestClient().get("/api/internal/export/business/$userId")

        then()
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }
}
