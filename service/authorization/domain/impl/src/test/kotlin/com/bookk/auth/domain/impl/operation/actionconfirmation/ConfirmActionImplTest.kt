package com.bookk.auth.domain.impl.operation.actionconfirmation

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationRequest
import com.bookk.auth.domain.api.actionconfirmation.entity.ConfirmableAction
import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.actionconfirmation.ActionConfirmationClaims
import library.signing.TokenAudience
import library.signing.TokenIssuer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class ConfirmActionImplTest {

    private class SutFixture(
        val userId: Uuid = Uuid.random(),
        action: ConfirmableAction = ConfirmableAction.DELETE_CLIENT,
    ) {
        val finishAssertion = mockk<FinishAssertion>()
        val tokenIssuer = mockk<TokenIssuer>()
        val sut = ConfirmActionImpl(finishAssertion, tokenIssuer)
        val request = ActionConfirmationRequest(
            requestId = "request-id",
            publicKeyCredentialJson = "{}",
            action = action,
            businessId = Uuid.random(),
            targetId = Uuid.random(),
        )

        init {
            coEvery { tokenIssuer.issue(any(), any()) } coAnswers {
                val modifier = secondArg<JWTCreator.Builder.() -> JWTCreator.Builder>()
                JWT.create().modifier().sign(Algorithm.HMAC256("test-secret"))
            }
        }

        fun passkeyOwnedBy(ownerId: Uuid) {
            coEvery { finishAssertion(request) } returns
                Result.success(PasskeyCredential.stub(authInfo = Authentication.stub(userId = ownerId)))
        }
    }

    @Test
    fun `should issue a short-lived confirmation token bound to caller, action, business and target`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.passkeyOwnedBy(fixture.userId)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.request)

        then()
        val decoded = JWT.decode(result.getOrThrow().token)
        assertEquals(listOf(TokenAudience.actionConfirmation), decoded.audience)
        assertEquals(fixture.userId.toString(), decoded.getClaim(ActionConfirmationClaims.CLAIM_USER_ID).asString())
        assertEquals(
            ActionConfirmationClaims.ACTION_DELETE_CLIENT,
            decoded.getClaim(ActionConfirmationClaims.CLAIM_ACTION).asString(),
        )
        assertEquals(
            fixture.request.businessId.toString(),
            decoded.getClaim(ActionConfirmationClaims.CLAIM_BUSINESS_ID).asString(),
        )
        assertEquals(
            fixture.request.targetId.toString(),
            decoded.getClaim(ActionConfirmationClaims.CLAIM_TARGET_ID).asString(),
        )
        assertNotNull(decoded.id)
        coVerify(exactly = 1) { fixture.tokenIssuer.issue(ConfirmActionImpl.ACTION_CONFIRMATION_TTL, any()) }
    }

    @Test
    fun `should name each requested action in the action confirmation token`() = runUnitTest {
        given()
        val expectedClaims = mapOf(
            ConfirmableAction.DELETE_CLIENT to ActionConfirmationClaims.ACTION_DELETE_CLIENT,
            ConfirmableAction.EXPORT_PERSONAL_DATA to ActionConfirmationClaims.ACTION_EXPORT_PERSONAL_DATA,
            ConfirmableAction.EXPORT_BUSINESS_DATA to ActionConfirmationClaims.ACTION_EXPORT_BUSINESS_DATA,
            ConfirmableAction.DELETE_ACCOUNT to ActionConfirmationClaims.ACTION_DELETE_ACCOUNT,
        )

        whenn()
        val claims = ConfirmableAction.entries.associateWith { action ->
            val fixture = SutFixture(action = action)
            fixture.passkeyOwnedBy(fixture.userId)
            JWT.decode(
                fixture.sut(fixture.userId, fixture.request).getOrThrow().token,
            ).getClaim(ActionConfirmationClaims.CLAIM_ACTION).asString()
        }

        then()
        assertEquals(expectedClaims, claims)
    }

    @Test
    fun `should give every action confirmation token its own id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.passkeyOwnedBy(fixture.userId)

        whenn()
        val first = fixture.sut(fixture.userId, fixture.request).getOrThrow()
        val second = fixture.sut(fixture.userId, fixture.request).getOrThrow()

        then()
        assertNotEquals(JWT.decode(first.token).id, JWT.decode(second.token).id)
    }

    @Test
    fun `should not issue a token for a passkey that belongs to another account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.passkeyOwnedBy(Uuid.random())

        whenn()
        val result = fixture.sut(fixture.userId, fixture.request)

        then()
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.PasskeyNotOwned)
        coVerify(exactly = 0) { fixture.tokenIssuer.issue(any(), any()) }
    }

    @Test
    fun `should not issue a token when the passkey assertion fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.finishAssertion(fixture.request) } returns
            Result.failure(FinishAssertion.Error.ChallengeWindowExpired())

        whenn()
        val result = fixture.sut(fixture.userId, fixture.request)

        then()
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.ChallengeWindowExpired)
        coVerify(exactly = 0) { fixture.tokenIssuer.issue(any(), any()) }
    }
}
