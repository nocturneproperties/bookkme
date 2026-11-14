package com.bookk.user.microservice.route.api.internal

import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import com.bookk.user.domain.api.entity.EmailBody
import com.bookk.user.domain.api.entity.LegalDocumentError
import com.bookk.user.domain.api.entity.NewUser
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.entity.UserId
import com.bookk.user.domain.api.error.UserErrorCodes
import com.bookk.user.domain.api.operation.CreateUser
import com.bookk.user.domain.api.operation.GetUserByEmail
import com.bookk.user.domain.api.operation.GetUserById
import com.bookk.user.microservice.route.UserRouting
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class InternalUserTest {

    private val userId = Uuid.random()
    private val email = "test@example.com"

    @Test
    fun `should create user with the documents it accepted`() = routeTest {
        given()
        val useCase: CreateUser = mockk()
        val newUser =
            NewUser("Ann", "Lee", email, AcceptedLegalDocuments(privacyPolicy = "2026-01-01", termsOfService = null))

        coEvery { useCase.invoke(newUser) } returns Result.success(UserId(userId))

        setupApplication(
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { postCreateUser() },
        )

        whenn()
        val client = createTestClient()
        val response = client.post(UserRouting.Api.Internal.User()) {
            setBody(newUser)
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(userId, response.body<UserId>().id)
    }

    @Test
    fun `should reject a body without accepted documents from an older auth service`() = routeTest {
        given()
        val useCase: CreateUser = mockk()

        setupApplication(
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { postCreateUser() },
        )

        whenn()
        val client = createTestClient()
        val response = client.post(UserRouting.Api.Internal.User()) {
            setBody(User.stub(email = email))
        }

        then()
        assertEquals(HttpStatusCode.BadRequest, response.status)
        coVerify(exactly = 0) { useCase.invoke(any()) }
    }

    @Test
    fun `should return 422 when a legal document in effect is not accepted`() = routeTest {
        given()
        val useCase: CreateUser = mockk()
        coEvery { useCase.invoke(any()) } returns Result.failure(CreateUser.Error.LegalDocumentsNotAccepted())

        setupApplication(
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { postCreateUser() },
        )

        whenn()
        val client = createTestClient()
        val response = client.post(UserRouting.Api.Internal.User()) {
            setBody(NewUser("Ann", "Lee", email, AcceptedLegalDocuments(privacyPolicy = null, termsOfService = null)))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(UserErrorCodes.LEGAL_DOCUMENTS_NOT_ACCEPTED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return 422 when an accepted version is not published`() = routeTest {
        given()
        val useCase: CreateUser = mockk()
        coEvery { useCase.invoke(any()) } returns Result.failure(LegalDocumentError.VersionUnknown())

        setupApplication(
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { postCreateUser() },
        )

        whenn()
        val client = createTestClient()
        val response = client.post(UserRouting.Api.Internal.User()) {
            setBody(
                NewUser(
                    "Ann",
                    "Lee",
                    email,
                    AcceptedLegalDocuments(privacyPolicy = "unpublished", termsOfService = null),
                ),
            )
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(UserErrorCodes.LEGAL_DOCUMENT_VERSION_UNKNOWN, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return user by email`() = routeTest {
        given()
        val useCase: GetUserByEmail = mockk()
        val user = User.stub(id = userId, email = email)
        val emailBody = EmailBody(email)

        coEvery { useCase.invoke(emailBody) } returns Result.success(user)

        setupApplication(
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { getUserByEmail() },
        )

        whenn()
        val client = createTestClient()
        val response = client.get(UserRouting.Api.Internal.User.Email()) {
            setBody(emailBody)
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return user by id`() = routeTest {
        given()
        val useCase: GetUserById = mockk()
        val user = User.stub(id = userId, email = email)

        coEvery { useCase.invoke(userId) } returns Result.success(user)

        setupApplication(
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { getUserById() },
        )

        whenn()
        val client = createTestClient()
        val response = client.get(UserRouting.Api.Internal.User.Id(id = userId))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
