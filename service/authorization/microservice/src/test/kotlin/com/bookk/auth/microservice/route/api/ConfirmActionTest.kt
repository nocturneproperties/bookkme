package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationRequest
import com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationToken
import com.bookk.auth.domain.api.actionconfirmation.entity.ConfirmableAction
import com.bookk.auth.domain.api.actionconfirmation.operation.ConfirmAction
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion
import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.microservice.route.AuthRouting
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class ConfirmActionTest {

    private fun request() = ActionConfirmationRequest(
        requestId = "request-id",
        publicKeyCredentialJson = "{}",
        action = ConfirmableAction.DELETE_CLIENT,
        businessId = Uuid.random(),
        targetId = Uuid.random(),
    )

    @Test
    fun `should return the action confirmation token for the caller`() = routeTest {
        given()
        val useCase: ConfirmAction = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, any()) } returns Result.success(ActionConfirmationToken("action-confirmation-token"))
        setupApplication(
            extension = {
                install(Authentication) {
                    provider { authenticate { context -> context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) } }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { confirmAction() },
        )

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.PassKey.ConfirmAction()) { setBody(request()) }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ActionConfirmationToken("action-confirmation-token"), response.body<ActionConfirmationToken>())
        coVerify(exactly = 1) { useCase.invoke(userId, any()) }
    }

    private suspend fun ApplicationTestBuilder.rejectedWith(error: BusinessError): HttpResponse {
        val useCase: ConfirmAction = mockk()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(error)
        setupApplication(
            extension = {
                install(Authentication) {
                    provider { authenticate { context -> context.principal(AppPrincipal(Uuid.random(), Uuid.random(), Uuid.random())) } }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { confirmAction() },
        )
        return createTestClient().post(AuthRouting.Api.Auth.PassKey.ConfirmAction()) { setBody(request()) }
    }

    @Test
    fun `should return unprocessable entity when the challenge window expired`() = routeTest {
        given()
        val error = FinishAssertion.Error.ChallengeWindowExpired()

        whenn()
        val response = rejectedWith(error)

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.CHALLENGE_WINDOW_EXPIRED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when passkey verification fails`() = routeTest {
        given()
        val error = FinishAssertion.Error.VerificationFailed()

        whenn()
        val response = rejectedWith(error)

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.VERIFICATION_FAILED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when the passkey owner is not found`() = routeTest {
        given()
        val error = FinishAssertion.Error.PasskeyOwnerNotFound()

        whenn()
        val response = rejectedWith(error)

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.PASSKEY_OWNER_NOT_FOUND, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when the passkey belongs to another account`() = routeTest {
        given()
        val error = FinishAssertion.Error.PasskeyNotOwned()

        whenn()
        val response = rejectedWith(error)

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.PASSKEY_NOT_OWNED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized without authentication`() = routeTest {
        given()
        val useCase: ConfirmAction = mockk()
        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { confirmAction() },
        )

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.PassKey.ConfirmAction()) { setBody(request()) }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
