package com.bookk.auth.domain.impl.operation.registration

import com.bookk.auth.domain.api.registration.entity.CreateAccountRequest
import com.bookk.auth.domain.api.registration.entity.RegistrationChallengeResponse
import com.bookk.auth.domain.api.registration.operation.StartPasskeyRegistration
import com.bookk.auth.domain.api.registration.operation.StartRegistration
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.UserSnapshot
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class StartRegistrationImplTest {

    private class SutFixture {
        val userClient = mockk<UserClient>()
        val startPasskeyRegistration = mockk<StartPasskeyRegistration>()
        val sut = StartRegistrationImpl(userClient, startPasskeyRegistration)
    }

    private fun makeChallengeResponse(): RegistrationChallengeResponse = RegistrationChallengeResponse(
        requestId = "req-id",
        challenge = "challenge",
        challengeJson = "{}",
        userHandle = "handle",
        displayName = "John Doe"
    )

    private val validRequest = CreateAccountRequest(
        firstName = "John",
        lastName = "Doe",
        email = "john.doe@example.com"
    )

    @Test
    fun `should start registration successfully when email is valid and not taken`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val challenge = makeChallengeResponse()
        with(fixture) {
            coEvery { userClient.getUserByEmail(validRequest.email) } returns Result.failure(
                object : BusinessError(HttpStatusCode.NotFound.value, 0, "not found") {}
            )
            coEvery { startPasskeyRegistration(any(), any()) } returns Result.success(challenge)
        }

        whenn()
        val result = fixture.sut.invoke(validRequest)

        then()
        assertTrue(result.isSuccess)
        assertEquals(challenge, result.getOrNull())
    }

    @Test
    fun `should return InvalidEmailFormat when email format is invalid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val invalidRequest = CreateAccountRequest(
            firstName = "John",
            lastName = "Doe",
            email = "not-an-email"
        )

        whenn()
        val result = fixture.sut.invoke(invalidRequest)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StartRegistration.Error.InvalidEmailFormat)
    }

    @Test
    fun `should return EmailAlreadyExist when user with email already exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existingUser = UserSnapshot.stub(email = validRequest.email)
        with(fixture) {
            coEvery { userClient.getUserByEmail(validRequest.email) } returns Result.success(existingUser)
        }

        whenn()
        val result = fixture.sut.invoke(validRequest)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StartRegistration.Error.EmailAlreadyExist)
    }

    @Test
    fun `should propagate error when user client returns non-404 business error`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val serverError = object : BusinessError(HttpStatusCode.InternalServerError.value, 0, "server error") {}
        with(fixture) {
            coEvery { userClient.getUserByEmail(validRequest.email) } returns Result.failure(serverError)
        }

        whenn()
        val result = fixture.sut.invoke(validRequest)

        then()
        assertTrue(result.isFailure)
        assertEquals(serverError, result.exceptionOrNull())
    }
}
