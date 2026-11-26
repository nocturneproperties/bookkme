package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.email.operation.ConfirmEmailChange
import com.bookk.auth.domain.api.email.operation.StartEmailChange
import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.identification.entity.AddPasskeyRequest
import com.bookk.auth.domain.api.identification.operation.GetAttachPasskeyToAccountChallenge
import com.bookk.auth.domain.api.registration.operation.AttachNewPasskeyToAccount
import com.bookk.auth.microservice.route.AuthRouting
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
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class DemoGuardTest {

    private class Operations {
        val startEmailChange: StartEmailChange = mockk()
        val confirmEmailChange: ConfirmEmailChange = mockk()
        val getChallenge: GetAttachPasskeyToAccountChallenge = mockk()
        val attachPasskey: AttachNewPasskeyToAccount = mockk()
    }

    private fun ApplicationTestBuilder.signedInAsDemo(operations: Operations) {
        val principal = AppPrincipal(Uuid.random(), Uuid.random(), Uuid.random(), isDemo = true)
        setupApplication(
            extension = {
                install(Authentication) { provider { authenticate { it.principal(principal) } } }
            },
            diModule = module {
                single { operations.startEmailChange }
                single { operations.confirmEmailChange }
                single { operations.getChallenge }
                single { operations.attachPasskey }
            },
            routeUnderTest = {
                emailChange()
                passkeyOperations()
            },
        )
    }

    private suspend fun HttpResponse.errorCode(): Int {
        return body<SimpleServerError>().errorCode
    }

    @Test
    fun `should send a demo account to the upgrade instead of changing its email`() = routeTest {
        given()
        signedInAsDemo(Operations())

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Email()) { setBody(EmailChangeRequest("a@b.co")) }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.DEMO_UPGRADE_REQUIRED, response.errorCode())
    }

    @Test
    fun `should send a demo account to the upgrade instead of confirming an email`() = routeTest {
        given()
        signedInAsDemo(Operations())

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Email.Confirm()) {
            setBody(EmailConfirmation(Uuid.random(), "123456"))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.DEMO_UPGRADE_REQUIRED, response.errorCode())
    }

    @Test
    fun `should send a demo account to the upgrade instead of starting a passkey`() = routeTest {
        given()
        signedInAsDemo(Operations())

        whenn()
        val response = createTestClient().get(AuthRouting.Api.Auth.PassKey.AddChallenge())

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.DEMO_UPGRADE_REQUIRED, response.errorCode())
    }

    @Test
    fun `should send a demo account to the upgrade instead of adding a passkey`() = routeTest {
        given()
        signedInAsDemo(Operations())

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.PassKey.AddFinish()) {
            setBody(AddPasskeyRequest(requestId = "req-id", publicKeyCredentialJson = "{}"))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.DEMO_UPGRADE_REQUIRED, response.errorCode())
    }
}
