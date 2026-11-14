package com.bookk.user.microservice.route.api

import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import com.bookk.user.domain.api.error.UserErrorCodes
import com.bookk.user.domain.api.operation.DownloadPersonalDataExport
import com.bookk.user.domain.api.operation.GetPersonalDataExport
import com.bookk.user.domain.api.operation.StartPersonalDataExport
import com.bookk.user.microservice.route.UserRouting
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.mockk
import library.export.DataExport
import library.export.DataExportDocument
import library.export.DataExportStatus
import library.export.DataExports
import library.export.ExportErrorCodes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class PersonalDataExportTest {

    private val userId = Uuid.random()
    private val start: StartPersonalDataExport = mockk()
    private val status: GetPersonalDataExport = mockk()
    private val download: DownloadPersonalDataExport = mockk()
    private val running = DataExport.stub(
        status = DataExportStatus.RUNNING,
        requestedAt = Instant.fromEpochMilliseconds(1_000),
    )

    private fun io.ktor.server.testing.ApplicationTestBuilder.serve(authenticated: Boolean = true) = setupApplication(
        extension = { if (authenticated) authenticateAs(userId) else rejectEveryToken() },
        diModule = module {
            single { start }
            single { status }
            single { download }
        },
        routeUnderTest = { personalDataExport() },
    )

    @Test
    fun `should start the caller's export with their action confirmation token`() = routeTest {
        given()
        coEvery { start(userId, "action-confirmation-token") } returns Result.success(running)
        serve()

        whenn()
        val response = createTestClient().post(UserRouting.Api.User.Export()) {
            setBody(DataExportRequest(confirmationToken = "action-confirmation-token"))
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(running, response.body<DataExport>())
    }

    @Test
    fun `should refuse to start without action confirmation`() = routeTest {
        given()
        coEvery { start(userId, "") } returns Result.failure(StartPersonalDataExport.Error.ActionConfirmationInvalid())
        serve()

        whenn()
        val response = createTestClient().post(UserRouting.Api.User.Export())

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(UserErrorCodes.USER_ACTION_CONFIRMATION_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should refuse a second export within a day`() = routeTest {
        given()
        coEvery { start(userId, "action-confirmation-token") } returns Result.failure(DataExports.Error.TooFrequent())
        serve()

        whenn()
        val response = createTestClient().post(UserRouting.Api.User.Export()) {
            setBody(DataExportRequest(confirmationToken = "action-confirmation-token"))
        }

        then()
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals(ExportErrorCodes.DATA_EXPORT_TOO_FREQUENT, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should report the caller's export`() = routeTest {
        given()
        coEvery { status(userId) } returns Result.success(running)
        serve()

        whenn()
        val response = createTestClient().get(UserRouting.Api.User.Export())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(running, response.body<DataExport>())
    }

    @Test
    fun `should download the caller's ready document as JSON`() = routeTest {
        given()
        coEvery { download(userId) } returns Result.success(DataExportDocument(gzip("""{"userId":"$userId"}""")))
        serve()

        whenn()
        val response = createTestClient().get(UserRouting.Api.User.Export.Download())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"userId":"$userId"}""", response.bodyAsText())
        assertEquals("attachment; filename=bookk-personal-data.json", response.headers[HttpHeaders.ContentDisposition])
    }

    @Test
    fun `should answer 404 when no document is ready`() = routeTest {
        given()
        coEvery { download(userId) } returns Result.failure(Error.NotFound())
        serve()

        whenn()
        val response = createTestClient().get(UserRouting.Api.User.Export.Download()) {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }

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
            client.post(UserRouting.Api.User.Export()).status,
            client.get(UserRouting.Api.User.Export()).status,
            client.get(UserRouting.Api.User.Export.Download()).status,
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
