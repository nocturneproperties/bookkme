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
import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import com.bookk.user.domain.api.entity.LegalDocument
import com.bookk.user.domain.api.entity.LegalDocumentError
import com.bookk.user.domain.api.entity.LegalDocumentStatus
import com.bookk.user.domain.api.entity.LegalDocumentVersion
import com.bookk.user.domain.api.error.UserErrorCodes
import com.bookk.user.domain.api.operation.AcceptLegalDocuments
import com.bookk.user.domain.api.operation.GetLegalDocumentStatus
import com.bookk.user.domain.api.operation.GetLegalDocuments
import com.bookk.user.microservice.route.UserRouting
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class LegalDocumentsTest {

    @Test
    fun `should return the versions in effect without authentication`() = routeTest {
        given()
        val useCase: GetLegalDocuments = mockk()
        val versions = listOf(
            LegalDocumentVersion.stub(),
            LegalDocumentVersion.stub(document = LegalDocument.TERMS_OF_SERVICE, url = "https://bookk.me/terms"),
        )
        coEvery { useCase.invoke() } returns Result.success(versions)
        setupApplication(
            extension = { rejectEveryToken() },
            diModule = module { single { useCase } },
            routeUnderTest = { legalDocuments() },
        )

        whenn()
        val response = createTestClient().get(UserRouting.Api.User.Legal())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(versions, response.body<List<LegalDocumentVersion>>())
    }

    @Test
    fun `should return the caller's acceptance of each version in effect`() = routeTest {
        given()
        val userId = Uuid.random()
        val useCase: GetLegalDocumentStatus = mockk()
        val statuses = listOf(
            LegalDocumentStatus.stub(acceptedAt = Instant.fromEpochSeconds(1_700_000_000)),
            LegalDocumentStatus.stub(current = LegalDocumentVersion.stub(document = LegalDocument.TERMS_OF_SERVICE), acceptedAt = null),
        )
        coEvery { useCase.invoke(userId) } returns Result.success(statuses)
        setupApplication(
            extension = { authenticateAs(userId) },
            diModule = module { single { useCase } },
            routeUnderTest = { legalDocuments() },
        )

        whenn()
        val response = createTestClient().get(UserRouting.Api.User.Me.Legal())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(statuses, response.body<List<LegalDocumentStatus>>())
    }

    @Test
    fun `should return 401 for the caller's acceptances when unauthenticated`() = routeTest {
        given()
        val useCase: GetLegalDocumentStatus = mockk()
        setupApplication(
            extension = { rejectEveryToken() },
            diModule = module { single { useCase } },
            routeUnderTest = { legalDocuments() },
        )

        whenn()
        val response = createTestClient().get(UserRouting.Api.User.Me.Legal())

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should record the accepted versions`() = routeTest {
        given()
        val userId = Uuid.random()
        val useCase: AcceptLegalDocuments = mockk()
        val accepted = AcceptedLegalDocuments(privacyPolicy = "2026-01-01", termsOfService = "2025-06-01")
        coEvery { useCase.invoke(userId, accepted) } returns Result.success(Unit)
        setupApplication(
            extension = { authenticateAs(userId) },
            diModule = module { single { useCase } },
            routeUnderTest = { legalDocuments() },
        )

        whenn()
        val response = createTestClient().post(UserRouting.Api.User.Me.Legal()) { setBody(accepted) }

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
        coVerify(exactly = 1) { useCase.invoke(userId, accepted) }
    }

    @Test
    fun `should return 422 when an accepted version is not published`() = routeTest {
        given()
        val userId = Uuid.random()
        val useCase: AcceptLegalDocuments = mockk()
        coEvery { useCase.invoke(userId, any()) } returns Result.failure(LegalDocumentError.VersionUnknown())
        setupApplication(
            extension = { authenticateAs(userId) },
            diModule = module { single { useCase } },
            routeUnderTest = { legalDocuments() },
        )

        whenn()
        val response = createTestClient().post(UserRouting.Api.User.Me.Legal()) {
            setBody(AcceptedLegalDocuments(privacyPolicy = "unpublished", termsOfService = null))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(UserErrorCodes.LEGAL_DOCUMENT_VERSION_UNKNOWN, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return 404 when the caller's profile does not exist`() = routeTest {
        given()
        val userId = Uuid.random()
        val useCase: AcceptLegalDocuments = mockk()
        coEvery { useCase.invoke(userId, any()) } returns Result.failure(Error.NotFound())
        setupApplication(
            extension = { authenticateAs(userId) },
            diModule = module { single { useCase } },
            routeUnderTest = { legalDocuments() },
        )

        whenn()
        val response = createTestClient().post(UserRouting.Api.User.Me.Legal()) {
            setBody(AcceptedLegalDocuments(privacyPolicy = "2026-01-01", termsOfService = null))
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return 401 for an acceptance when unauthenticated`() = routeTest {
        given()
        val useCase: AcceptLegalDocuments = mockk()
        setupApplication(
            extension = { rejectEveryToken() },
            diModule = module { single { useCase } },
            routeUnderTest = { legalDocuments() },
        )

        whenn()
        val response = createTestClient().post(UserRouting.Api.User.Me.Legal()) {
            setBody(AcceptedLegalDocuments(privacyPolicy = "2026-01-01", termsOfService = null))
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { useCase.invoke(any(), any()) }
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
}
