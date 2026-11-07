package com.bookk.server.auth.client

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTCredential
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaInstant
import kotlin.uuid.Uuid

internal class AuthTokenValidatorTest {

    private class SutFixture {
        val call = mockk<ApplicationCall>()

        fun credential(expiresIn: Duration = 5.minutes, claims: JWTCreator.Builder.() -> JWTCreator.Builder = { this }) = JWTCredential(
            JWT.decode(
                JWT.create()
                    .withExpiresAt((Clock.System.now() + expiresIn).toJavaInstant())
                    .claims()
                    .sign(Algorithm.none()),
            ),
        )
    }

    @Test
    fun `should build the principal from an access token's account claims`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val userId = Uuid.random()
        val deviceId = Uuid.random()
        val credential = fixture.credential {
            withClaim(AuthClaim.AUTH_ID.key, authId.toString())
                .withClaim(AuthClaim.USER_ID.key, userId.toString())
                .withClaim(AuthClaim.DEVICE_ID.key, deviceId.toString())
        }

        whenn()
        val result = authTokenValidator(fixture.call, credential)

        then()
        assertEquals(listOf(authId, userId, deviceId), listOf(result?.authId, result?.userId, result?.deviceId))
    }

    @Test
    fun `should not authenticate a token without the account claims`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val credential = fixture.credential { withClaim("business_id", Uuid.random().toString()) }

        whenn()
        val result = authTokenValidator(fixture.call, credential)

        then()
        assertNull(result)
    }

    @Test
    fun `should not authenticate a token whose account claim is not a uuid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val credential = fixture.credential {
            withClaim(AuthClaim.AUTH_ID.key, "not-a-uuid")
                .withClaim(AuthClaim.USER_ID.key, Uuid.random().toString())
                .withClaim(AuthClaim.DEVICE_ID.key, Uuid.random().toString())
        }

        whenn()
        val result = authTokenValidator(fixture.call, credential)

        then()
        assertNull(result)
    }

    @Test
    fun `should not authenticate an expired token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val credential = fixture.credential(expiresIn = (-1).minutes) {
            withClaim(AuthClaim.AUTH_ID.key, Uuid.random().toString())
                .withClaim(AuthClaim.USER_ID.key, Uuid.random().toString())
                .withClaim(AuthClaim.DEVICE_ID.key, Uuid.random().toString())
        }

        whenn()
        val result = authTokenValidator(fixture.call, credential)

        then()
        assertNull(result)
    }
}
