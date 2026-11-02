package com.bookk.business.domain.impl.stepup

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.StepUpClaims
import io.mockk.every
import io.mockk.mockk
import library.signing.TokenAudience
import library.signing.TokenValidator
import library.signing.TokenValidatorFactory
import library.signing.ValidationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaInstant
import kotlin.uuid.Uuid

internal class StepUpVerifierTest {

    private class SutFixture {
        val algorithm: Algorithm = Algorithm.HMAC256("test-secret")
        val tokenValidatorFactory = mockk<TokenValidatorFactory>()
        val sut = StepUpVerifier(tokenValidatorFactory)
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val clientId = Uuid.random()

        init {
            val validator = mockk<TokenValidator>()
            every { validator.verifier } returns JWT.require(algorithm).withIssuer(ISSUER).withAudience(TokenAudience.stepUp).build()
            every { tokenValidatorFactory.forType(ValidationType.STEP_UP) } returns validator
        }

        fun token(
            userId: Uuid = this.userId,
            action: String = StepUpClaims.ACTION_DELETE_CLIENT,
            businessId: Uuid = this.businessId,
            targetId: Uuid = this.clientId,
            tokenId: String? = "token-id",
            expiresIn: Duration = 2.minutes,
        ): String = JWT.create()
            .withIssuer(ISSUER)
            .withAudience(TokenAudience.stepUp)
            .withExpiresAt((Clock.System.now() + expiresIn).toJavaInstant())
            .apply { if (tokenId != null) withJWTId(tokenId) }
            .withClaim(StepUpClaims.CLAIM_USER_ID, userId.toString())
            .withClaim(StepUpClaims.CLAIM_ACTION, action)
            .withClaim(StepUpClaims.CLAIM_BUSINESS_ID, businessId.toString())
            .withClaim(StepUpClaims.CLAIM_TARGET_ID, targetId.toString())
            .sign(algorithm)

        fun verify(token: String) = sut.verify(token, userId, StepUpClaims.ACTION_DELETE_CLIENT, businessId, clientId)
    }

    @Test
    fun `should accept a step-up token for the caller, the action, the business and the target`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val proof = fixture.verify(fixture.token())

        then()
        assertEquals("token-id", proof?.tokenId)
        assertTrue(proof!!.remainingLifetime in 1.minutes..2.minutes)
    }

    @Test
    fun `should not accept a step-up token of another user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val proof = fixture.verify(fixture.token(userId = Uuid.random()))

        then()
        assertNull(proof)
    }

    @Test
    fun `should not accept a step-up token for another action`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val proof = fixture.verify(fixture.token(action = "business.delete"))

        then()
        assertNull(proof)
    }

    @Test
    fun `should not accept a step-up token for another business`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val proof = fixture.verify(fixture.token(businessId = Uuid.random()))

        then()
        assertNull(proof)
    }

    @Test
    fun `should not accept a step-up token for another client`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val proof = fixture.verify(fixture.token(targetId = Uuid.random()))

        then()
        assertNull(proof)
    }

    @Test
    fun `should not accept a step-up token without an id`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val proof = fixture.verify(fixture.token(tokenId = null))

        then()
        assertNull(proof)
    }

    @Test
    fun `should not accept an expired step-up token`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val proof = fixture.verify(fixture.token(expiresIn = (-1).seconds))

        then()
        assertNull(proof)
    }

    @Test
    fun `should not accept an access token as a step-up token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val accessToken = JWT.create()
            .withIssuer(ISSUER)
            .withAudience(TokenAudience.access)
            .withExpiresAt((Clock.System.now() + 2.minutes).toJavaInstant())
            .withJWTId("token-id")
            .sign(fixture.algorithm)

        whenn()
        val proof = fixture.verify(accessToken)

        then()
        assertNull(proof)
    }

    @Test
    fun `should not accept a step-up token without an expiry`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val neverExpiring = JWT.create()
            .withIssuer(ISSUER)
            .withAudience(TokenAudience.stepUp)
            .withJWTId("token-id")
            .withClaim(StepUpClaims.CLAIM_USER_ID, fixture.userId.toString())
            .withClaim(StepUpClaims.CLAIM_ACTION, StepUpClaims.ACTION_DELETE_CLIENT)
            .withClaim(StepUpClaims.CLAIM_BUSINESS_ID, fixture.businessId.toString())
            .withClaim(StepUpClaims.CLAIM_TARGET_ID, fixture.clientId.toString())
            .sign(fixture.algorithm)

        whenn()
        val proof = fixture.verify(neverExpiring)

        then()
        assertNull(proof)
    }

    @Test
    fun `should not accept a malformed token`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val proof = fixture.verify("not-a-token")

        then()
        assertNull(proof)
    }

    private companion object {
        const val ISSUER = "auth.service"
    }
}
