package com.bookk.auth.domain.impl.operation.token

import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Source
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RefreshTokenImplTest {

    private class SutFixture {
        val generateAuthToken = mockk<GenerateAuthToken>()

        val sut = RefreshTokenImpl(generateAuthToken)
    }

    @Test
    fun `should forward the raw token to generate auth token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = "some-opaque-token"
        val tokens = AuthTokens(accessToken = "access", refreshToken = "refresh")

        coEvery { fixture.generateAuthToken(any<Source.RefreshToken>()) } returns Result.success(tokens)

        whenn()
        val result = fixture.sut.invoke(token)

        then()
        assertEquals(tokens, result.getOrThrow())
        coVerify(exactly = 1) {
            fixture.generateAuthToken(match { it is Source.RefreshToken && it.token == token })
        }
    }
}
