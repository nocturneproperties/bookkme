package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.registration.entity.VerifyAccountCreationRequest
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration.Error.ChallengeWindowExpired
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration.Error.VerificationFailed
import com.bookk.auth.domain.api.registration.operation.FinishRegistration
import com.bookk.auth.domain.api.registration.operation.FinishRegistration.Error.AccountCreationFailed
import com.bookk.auth.domain.api.registration.operation.FinishRegistration.Error.InvalidEmailFormat
import com.bookk.auth.domain.api.registration.operation.FinishRegistration.Error.UserAlreadyExist
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.microservice.route.AuthRouting
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import com.bookk.user.domain.api.entity.LegalDocumentError
import com.bookk.user.domain.api.error.UserErrorCodes
import com.bookk.user.domain.api.operation.CreateUser
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import library.email.EmailCodeError
import library.email.EmailErrorCodes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

class PostValidateRegistrationTest {

    private fun createSimpleRequest(
        acceptedDocuments: AcceptedLegalDocuments = AcceptedLegalDocuments(privacyPolicy = "2026-01-01", termsOfService = "2025-06-01"),
    ): VerifyAccountCreationRequest = VerifyAccountCreationRequest(
        requestId = "user_id",
        deviceInfo = VerifyAccountCreationRequest.DeviceInfo(
            deviceUUID = Uuid.random(),
            deviceName = "example_device_name",
        ),
        userInfo = VerifyAccountCreationRequest.UserInfo(
            name = "name",
            lastName = "lastName",
            email = "email",
        ),
        publicKeyCredentialJson = "mock",
        acceptedDocuments = acceptedDocuments,
        verificationId = Uuid.random(),
        emailCode = "123456",
    )

    @Test
    fun `should pass the accepted legal documents to sign-up`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest(AcceptedLegalDocuments(privacyPolicy = "2026-01-01", termsOfService = "2025-06-01"))
        coEvery { useCase.invoke(any(), any()) } returns Result.success(AuthTokens("access", "refresh"))
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() },
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        then()
        assertEquals(HttpStatusCode.OK, response.status)
        coVerify { useCase.invoke(eq(request), any()) }
    }

    @Test
    fun `should return bad request when an older app sends no accepted legal documents`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() },
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(SignUpWithoutAcceptedDocuments(request.requestId, request.deviceInfo, request.userInfo, request.publicKeyCredentialJson))
        }
        then()
        assertEquals(HttpStatusCode.BadRequest, response.status)
        coVerify(exactly = 0) { useCase.invoke(any(), any()) }
    }

    @Test
    fun `should return unprocessable entity when a legal document in effect is not accepted`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest(AcceptedLegalDocuments(privacyPolicy = null, termsOfService = null))
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(CreateUser.Error.LegalDocumentsNotAccepted())
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() },
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(UserErrorCodes.LEGAL_DOCUMENTS_NOT_ACCEPTED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when an accepted legal document version is not published`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest(AcceptedLegalDocuments(privacyPolicy = "unpublished", termsOfService = null))
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(LegalDocumentError.VersionUnknown())
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() },
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(UserErrorCodes.LEGAL_DOCUMENT_VERSION_UNKNOWN, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when email format is invalid`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(InvalidEmailFormat())
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() },
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()
        then()
        coVerify { useCase.invoke(eq(request), any()) }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.INVALID_EMAIL_FORMAT, body.errorCode)
        assertEquals(InvalidEmailFormat().message, body.message)
    }

    @Test
    fun `should return unprocessable entity when user already exists`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(UserAlreadyExist())
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() },
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()
        then()
        coVerify { useCase.invoke(eq(request), any()) }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.USER_ALREADY_EXIST, body.errorCode)
        assertEquals(UserAlreadyExist().message, body.message)
    }

    @Test
    fun `should return unprocessable entity when challenge window is expired`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(ChallengeWindowExpired())
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() },
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()
        then()
        coVerify { useCase.invoke(eq(request), any()) }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.CHALLENGE_WINDOW_EXPIRED, body.errorCode)
        assertEquals(ChallengeWindowExpired().message, body.message)
    }

    @Test
    fun `should return unprocessable entity when verification fails`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(VerificationFailed())
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() },
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()
        then()
        coVerify { useCase.invoke(eq(request), any()) }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.VERIFICATION_FAILED, body.errorCode)
        assertEquals(VerificationFailed().message, body.message)
    }

    @Test
    fun `should return unprocessable entity when account creation fails`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(AccountCreationFailed())
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() },
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()
        then()
        coVerify { useCase.invoke(eq(request), any()) }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.ACCOUNT_CREATION_FAILED, body.errorCode)
        assertEquals(AccountCreationFailed().message, body.message)
    }

    @Test
    fun `should return OK and tokens when registration is completed`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        val expected = AuthTokens("access_token", "refresh_token")
        coEvery { useCase.invoke(any(), any()) } returns Result.success(expected)
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() },
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        val body = response.body<AuthTokens>()
        then()
        coVerify { useCase.invoke(eq(request), any()) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(expected, body)
    }

    @Test
    fun `should pass parsed language from Accept-Language header`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        val expected = AuthTokens("access_token", "refresh_token")
        coEvery { useCase.invoke(any(), any()) } returns Result.success(expected)
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() },
        )
        whenn()
        client.post(AuthRouting.Api.Auth.SignUp()) {
            header(HttpHeaders.AcceptLanguage, "uk-UA,uk;q=0.9")
            setBody(request)
        }
        then()
        coVerify { useCase.invoke(eq(request), eq(Language.UK)) }
    }

    @Test
    fun `should default to EN when Accept-Language header is missing`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        val expected = AuthTokens("access_token", "refresh_token")
        coEvery { useCase.invoke(any(), any()) } returns Result.success(expected)
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() },
        )
        whenn()
        client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        then()
        coVerify { useCase.invoke(eq(request), eq(Language.EN)) }
    }

    @Test
    fun `should return bad request when an older app sends no email code`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val request = createSimpleRequest()
        setupApplication(
            diModule = module { single<FinishRegistration> { useCase } },
            routeUnderTest = { registration() },
        )

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.SignUp()) {
            setBody(SignUpWithoutEmailCode(request.requestId, request.deviceInfo, request.userInfo, request.publicKeyCredentialJson, request.acceptedDocuments))
        }

        then()
        assertEquals(HttpStatusCode.BadRequest, response.status)
        coVerify(exactly = 0) { useCase.invoke(any(), any()) }
    }

    private suspend fun ApplicationTestBuilder.rejectedWith(error: BusinessError): HttpResponse {
        val useCase: FinishRegistration = mockk()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(error)
        setupApplication(
            diModule = module { single<FinishRegistration> { useCase } },
            routeUnderTest = { registration() },
        )
        return createTestClient().post(AuthRouting.Api.Auth.SignUp()) { setBody(createSimpleRequest()) }
    }

    @Test
    fun `should return unprocessable entity when the email code is wrong`() = routeTest {
        given()
        val response = rejectedWith(EmailCodeError.Invalid())

        whenn()
        val body = response.body<SimpleServerError>()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(EmailErrorCodes.EMAIL_CODE_INVALID, body.errorCode)
    }

    @Test
    fun `should return unprocessable entity when the email code expired`() = routeTest {
        given()
        val response = rejectedWith(EmailCodeError.Expired())

        whenn()
        val body = response.body<SimpleServerError>()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(EmailErrorCodes.EMAIL_CODE_EXPIRED, body.errorCode)
    }

    @Test
    fun `should return unprocessable entity after too many wrong email codes`() = routeTest {
        given()
        val response = rejectedWith(EmailCodeError.AttemptsExceeded())

        whenn()
        val body = response.body<SimpleServerError>()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(EmailErrorCodes.EMAIL_CODE_ATTEMPTS_EXCEEDED, body.errorCode)
    }

    @Serializable
    private class SignUpWithoutEmailCode(
        @ProtoNumber(1) val requestId: String,
        @ProtoNumber(2) val deviceInfo: VerifyAccountCreationRequest.DeviceInfo,
        @ProtoNumber(3) val userInfo: VerifyAccountCreationRequest.UserInfo,
        @ProtoNumber(4) val publicKeyCredentialJson: String,
        @ProtoNumber(5) val acceptedDocuments: AcceptedLegalDocuments,
    )

    @Serializable
    private class SignUpWithoutAcceptedDocuments(
        @ProtoNumber(1) val requestId: String,
        @ProtoNumber(2) val deviceInfo: VerifyAccountCreationRequest.DeviceInfo,
        @ProtoNumber(3) val userInfo: VerifyAccountCreationRequest.UserInfo,
        @ProtoNumber(4) val publicKeyCredentialJson: String,
    )
}
