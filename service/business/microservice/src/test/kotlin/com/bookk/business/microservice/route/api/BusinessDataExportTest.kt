package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.business.domain.api.export.DownloadBusinessDataExport
import com.bookk.business.domain.api.export.GetBusinessDataExport
import com.bookk.business.domain.api.export.StartBusinessDataExport
import com.bookk.business.microservice.route.BusinessRouting
import com.bookk.core.domain.entity.CoreErrorCodes
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.mockk
import library.export.DataExport
import library.export.DataExportDocument
import library.export.DataExportStatus
import library.export.DataExports
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.uuid.Uuid

internal class BusinessDataExportTest {

    private val userId = Uuid.random()
    private val businessId = Uuid.random()
    private val start: StartBusinessDataExport = mockk()
    private val status: GetBusinessDataExport = mockk()
    private val download: DownloadBusinessDataExport = mockk()
    private val running = DataExport.stub(status = DataExportStatus.RUNNING)

    private fun exportResource() = BusinessRouting.Api.Business.Id.Export(BusinessRouting.Api.Business.Id(id = businessId))

    private fun ApplicationTestBuilder.serve(authenticated: Boolean = true) = setupApplication(
        extension = { if (authenticated) authenticateAs(userId) else rejectEveryToken() },
        diModule = module {
            single { start }
            single { status }
            single { download }
        },
        routeUnderTest = { businessDataExport() },
    )

    @Test
    fun `should start the business export with the owner's step-up token`() = routeTest {
        given()
        coEvery { start(userId, businessId, "step-up-token") } returns Result.success(running)
        serve()

        whenn()
        val response = createTestClient().post(exportResource()) { setBody(DataExportRequest(stepUpToken = "step-up-token")) }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(running, response.body<DataExport>())
    }

    @Test
    fun `should refuse to start without a confirmed step-up`() = routeTest {
        given()
        coEvery { start(userId, businessId, "") } returns Result.failure(StartBusinessDataExport.Error.StepUpInvalid())
        serve()

        whenn()
        val response = createTestClient().post(exportResource())

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_STEP_UP_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should refuse a second export within a day`() = routeTest {
        given()
        coEvery { start(userId, businessId, "step-up-token") } returns Result.failure(DataExports.Error.TooFrequent())
        serve()

        whenn()
        val response = createTestClient().post(exportResource()) { setBody(DataExportRequest(stepUpToken = "step-up-token")) }

        then()
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals(CoreErrorCodes.DATA_EXPORT_TOO_FREQUENT, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 404 to anyone but the owner`() = routeTest {
        given()
        coEvery { start(userId, businessId, "step-up-token") } returns Result.failure(Error.OperationNotAllowed())
        coEvery { status(userId, businessId) } returns Result.failure(Error.OperationNotAllowed())
        coEvery { download(userId, businessId) } returns Result.failure(Error.OperationNotAllowed())
        serve()

        whenn()
        val client = createTestClient()
        val statuses = listOf(
            client.post(exportResource()) { setBody(DataExportRequest(stepUpToken = "step-up-token")) }.status,
            client.get(exportResource()).status,
            client.get(BusinessRouting.Api.Business.Id.Export.Download(exportResource())).status,
        )

        then()
        assertEquals(List(3) { HttpStatusCode.NotFound }, statuses)
    }

    @Test
    fun `should report the business's export`() = routeTest {
        given()
        coEvery { status(userId, businessId) } returns Result.success(running)
        serve()

        whenn()
        val response = createTestClient().get(exportResource())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(running, response.body<DataExport>())
    }

    @Test
    fun `should download the ready document as JSON`() = routeTest {
        given()
        coEvery { download(userId, businessId) } returns Result.success(DataExportDocument(gzip("""{"businessId":"$businessId"}""")))
        serve()

        whenn()
        val response = createTestClient().get(BusinessRouting.Api.Business.Id.Export.Download(exportResource()))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"businessId":"$businessId"}""", response.bodyAsText())
        assertEquals("attachment; filename=bookk-business-data.json", response.headers[HttpHeaders.ContentDisposition])
    }

    @Test
    fun `should answer 404 when no document is ready`() = routeTest {
        given()
        coEvery { download(userId, businessId) } returns Result.failure(Error.NotFound())
        serve()

        whenn()
        val response = createTestClient().get(BusinessRouting.Api.Business.Id.Export.Download(exportResource()))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should require a signed-in caller for every export route`() = routeTest {
        given()
        serve(authenticated = false)

        whenn()
        val client = createTestClient()
        val statuses = listOf(
            client.post(exportResource()).status,
            client.get(exportResource()).status,
            client.get(BusinessRouting.Api.Business.Id.Export.Download(exportResource())).status,
        )

        then()
        assertEquals(List(3) { HttpStatusCode.Unauthorized }, statuses)
    }

    private fun Application.authenticateAs(userId: Uuid) {
        install(Authentication) {
            provider {
                authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
            }
        }
    }

    private fun Application.rejectEveryToken() {
        install(Authentication) { bearer { authenticate { null } } }
    }

    private fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { bytes ->
        GZIPOutputStream(bytes).use { it.write(text.encodeToByteArray()) }
    }.toByteArray()
}
