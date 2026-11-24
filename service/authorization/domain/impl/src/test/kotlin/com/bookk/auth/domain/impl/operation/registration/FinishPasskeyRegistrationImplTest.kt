package com.bookk.auth.domain.impl.operation.registration

import com.bookk.auth.domain.api.identification.entity.AddPasskeyRequest
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.auth.domain.impl.passkey.TestPasskeyAuthenticator
import com.bookk.auth.domain.impl.passkey.createRelyingParty
import com.bookk.auth.domain.repository.CacheableCredentialRepository
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.yubico.webauthn.StartRegistrationOptions
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import com.yubico.webauthn.data.ResidentKeyRequirement
import com.yubico.webauthn.data.UserIdentity
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid
import com.yubico.webauthn.data.ByteArray as YubicoByteArray

internal class FinishPasskeyRegistrationImplTest {

    private class SutFixture {
        val passKeyDataSource = mockk<PassKeyDataSource>()
        val credentialRepository = mockk<CacheableCredentialRepository>()
        val transactionManager = mockk<TransactionManager>()
        val sut = FinishPasskeyRegistrationImpl(passKeyDataSource, credentialRepository, transactionManager)

        val authenticator = TestPasskeyAuthenticator()
        val userHandle = Uuid.random()
        val requestId = Uuid.random().toString()
        val creationOptions: PublicKeyCredentialCreationOptions

        init {
            every { credentialRepository.getCredentialIdsForUsername(any()) } returns emptySet()
            creationOptions = createRelyingParty(credentialRepository).startRegistration(
                StartRegistrationOptions.builder()
                    .user(
                        UserIdentity.builder()
                            .name(requestId)
                            .displayName("Work phone")
                            .id(YubicoByteArray(userHandle.toByteArray()))
                            .build(),
                    )
                    .authenticatorSelection(
                        AuthenticatorSelectionCriteria.builder().residentKey(ResidentKeyRequirement.REQUIRED).build(),
                    )
                    .build(),
            )
        }

        val challenge: String = creationOptions.challenge.base64Url

        fun stubNewPasskey() {
            transactionManager.mockTransaction()
            coEvery { passKeyDataSource.takeCachedChallenge(requestId) } returns creationOptions.toJson()
            coJustRun { credentialRepository.lookupAllCache(any()) }
            every { credentialRepository.lookupAll(any()) } returns emptySet()
        }
    }

    @Test
    fun `should verify a new passkey and give it the user and name of the challenge`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubNewPasskey()
        val registration = fixture.authenticator.registration(fixture.challenge)

        whenn()
        val result = fixture.sut.verifyRequest(AddPasskeyRequest(fixture.requestId, registration))

        then()
        val passkey = requireNotNull(result.getOrNull())
        assertArrayEquals(fixture.authenticator.credentialId, passkey.credDescriptor.id)
        assertEquals(fixture.userHandle, passkey.handle)
        assertEquals("Work phone", passkey.name)
    }

    @Test
    fun `should return ChallengeWindowExpired when the challenge is gone or already taken`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubNewPasskey()
        coEvery { fixture.passKeyDataSource.takeCachedChallenge(fixture.requestId) } returns null
        val registration = fixture.authenticator.registration(fixture.challenge)

        whenn()
        val result = fixture.sut.verifyRequest(AddPasskeyRequest(fixture.requestId, registration))

        then()
        assertTrue(result.exceptionOrNull() is FinishPasskeyRegistration.Error.ChallengeWindowExpired)
    }

    @Test
    fun `should take the challenge once, so a replayed registration finds none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubNewPasskey()
        coEvery { fixture.passKeyDataSource.takeCachedChallenge(fixture.requestId) } returnsMany
            listOf(fixture.creationOptions.toJson(), null)
        val registration = fixture.authenticator.registration(fixture.challenge)

        whenn()
        val first = fixture.sut.verifyRequest(AddPasskeyRequest(fixture.requestId, registration))
        val replay = fixture.sut.verifyRequest(AddPasskeyRequest(fixture.requestId, registration))

        then()
        assertTrue(first.isSuccess)
        assertTrue(replay.exceptionOrNull() is FinishPasskeyRegistration.Error.ChallengeWindowExpired)
    }

    @Test
    fun `should return VerificationFailed for a passkey created for another challenge`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubNewPasskey()
        val otherChallenge = TestPasskeyAuthenticator.encode(ByteArray(32) { 7 })
        val registration = fixture.authenticator.registration(otherChallenge)

        whenn()
        val result = fixture.sut.verifyRequest(AddPasskeyRequest(fixture.requestId, registration))

        then()
        assertTrue(result.exceptionOrNull() is FinishPasskeyRegistration.Error.VerificationFailed)
    }

    @Test
    fun `should return VerificationFailed for a passkey created for another origin`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubNewPasskey()
        val registration = fixture.authenticator.registration(fixture.challenge, origin = "https://phishing.example")

        whenn()
        val result = fixture.sut.verifyRequest(AddPasskeyRequest(fixture.requestId, registration))

        then()
        assertTrue(result.exceptionOrNull() is FinishPasskeyRegistration.Error.VerificationFailed)
    }

    @Test
    fun `should return VerificationFailed for a malformed registration without taking the challenge`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            fixture.stubNewPasskey()

            whenn()
            val result = fixture.sut.verifyRequest(AddPasskeyRequest(fixture.requestId, """{"type":"public-key"}"""))

            then()
            assertTrue(result.exceptionOrNull() is FinishPasskeyRegistration.Error.VerificationFailed)
            coVerify(exactly = 0) { fixture.passKeyDataSource.takeCachedChallenge(any()) }
        }
}
