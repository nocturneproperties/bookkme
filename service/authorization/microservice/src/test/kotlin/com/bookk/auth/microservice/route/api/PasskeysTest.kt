package com.bookk.auth.microservice.route.api

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
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class PasskeysTest {

    private fun ApplicationTestBuilder.setupPasskeys(
        authId: Uuid,
        challengeOutcome: Result<Nothing>? = null,
        attachOutcome: Result<Unit>? = null,
    ) {
        val getChallenge: GetAttachPasskeyToAccountChallenge = mockk()
        val attachPasskey: AttachNewPasskeyToAccount = mockk()
        challengeOutcome?.let { coEvery { getChallenge(authId, any(), any()) } returns it }
        attachOutcome?.let { coEvery { attachPasskey(authId, any()) } returns it }
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { context ->
                            context.principal(AppPrincipal(authId, Uuid.random(), Uuid.random()))
                        }
                    }
                }
            },
            diModule = module {
                single { getChallenge }
                single { attachPasskey }
            },
            routeUnderTest = { passkeyOperations() },
        )
    }

    @Test
    fun `should return unprocessable entity when a challenge is asked for an account at the passkey limit`() =
        routeTest {
            given()
            val authId = Uuid.random()
            setupPasskeys(
                authId,
                challengeOutcome = Result.failure(GetAttachPasskeyToAccountChallenge.Error.PasskeysLimitReached()),
            )

            whenn()
            val response = createTestClient().get(AuthRouting.Api.Auth.PassKey.AddChallenge())

            then()
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals(AuthErrorCodes.PASSKEYS_LIMIT_REACHED, response.body<SimpleServerError>().errorCode)
        }

    @Test
    fun `should return unprocessable entity when a passkey is added to an account at the passkey limit`() =
        routeTest {
            given()
            val authId = Uuid.random()
            setupPasskeys(
                authId,
                attachOutcome = Result.failure(AttachNewPasskeyToAccount.Error.PasskeysLimitReached()),
            )

            whenn()
            val response = createTestClient().post(AuthRouting.Api.Auth.PassKey.AddFinish()) {
                setBody(AddPasskeyRequest(requestId = "req-id", publicKeyCredentialJson = "{}"))
            }

            then()
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals(AuthErrorCodes.PASSKEYS_LIMIT_REACHED, response.body<SimpleServerError>().errorCode)
        }

    @Test
    fun `should return no content when a passkey is added below the passkey limit`() = routeTest {
        given()
        val authId = Uuid.random()
        setupPasskeys(authId, attachOutcome = Result.success(Unit))

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.PassKey.AddFinish()) {
            setBody(AddPasskeyRequest(requestId = "req-id", publicKeyCredentialJson = "{}"))
        }

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `should return unauthorized when adding a passkey without authentication`() = routeTest {
        given()
        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { },
            routeUnderTest = { passkeyOperations() },
        )

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.PassKey.AddFinish()) {
            setBody(AddPasskeyRequest(requestId = "req-id", publicKeyCredentialJson = "{}"))
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
