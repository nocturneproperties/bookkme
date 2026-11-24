package com.bookk.auth.domain.impl.operation.authentication

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.authentication.entity.VerifySignInRequest
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
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
import com.yubico.webauthn.AssertionRequest
import com.yubico.webauthn.StartAssertionOptions
import com.yubico.webauthn.data.UserVerificationRequirement
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.uuid.Uuid

internal class FinishAssertionImplTest {

    private class SutFixture {
        val passKeyDataSource = mockk<PassKeyDataSource>()
        val credentialRepository = mockk<CacheableCredentialRepository>()
        val transactionManager = mockk<TransactionManager>()
        val sut = FinishAssertionImpl(passKeyDataSource, credentialRepository, transactionManager)

        val authenticator = TestPasskeyAuthenticator()
        val authentication = Authentication.stub()
        val userHandle = authentication.uuid.toByteArray()
        val storedCredential = PasskeyCredential.stub(authInfo = authentication)
        val requestId = Uuid.random().toString()
        val assertionRequest: AssertionRequest = createRelyingParty(credentialRepository).startAssertion(
            StartAssertionOptions.builder().userVerification(UserVerificationRequirement.REQUIRED).build(),
        )
        val challenge: String = assertionRequest.publicKeyCredentialRequestOptions.challenge.base64Url

        fun stubKnownPasskey() {
            transactionManager.mockTransaction()
            coEvery { passKeyDataSource.takeCachedChallenge(requestId) } returns assertionRequest.toJson()
            coJustRun { credentialRepository.cacheUsername(any()) }
            coJustRun { credentialRepository.lookupCache(any(), any()) }
            every { credentialRepository.getUsernameForUserHandle(any()) } returns Optional.of("username")
            every { credentialRepository.lookup(any(), any()) } returns
                Optional.of(authenticator.registeredCredential(userHandle))
            coEvery { passKeyDataSource.getCredentialBy(authentication.uuid, authenticator.credentialId) } returns
                storedCredential
            coJustRun { passKeyDataSource.markAsUsed(storedCredential.id) }
        }

        fun request(publicKeyCredentialJson: String): VerifySignInRequest {
            return VerifySignInRequest(
                requestId = requestId,
                publicKeyCredentialJson = publicKeyCredentialJson,
                deviceInfo = VerifySignInRequest.DeviceInfo(deviceUUID = Uuid.random(), deviceName = "Phone"),
            )
        }
    }

    @Test
    fun `should return the stored passkey of a verified assertion and mark it used`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubKnownPasskey()
        val assertion = fixture.authenticator.assertion(fixture.challenge, fixture.userHandle)

        whenn()
        val result = fixture.sut.invoke(fixture.request(assertion))

        then()
        assertEquals(fixture.storedCredential, result.getOrNull())
        coVerify(exactly = 1) { fixture.passKeyDataSource.markAsUsed(fixture.storedCredential.id) }
    }

    @Test
    fun `should return ChallengeWindowExpired when the challenge is gone or already taken`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubKnownPasskey()
        coEvery { fixture.passKeyDataSource.takeCachedChallenge(fixture.requestId) } returns null
        val assertion = fixture.authenticator.assertion(fixture.challenge, fixture.userHandle)

        whenn()
        val result = fixture.sut.invoke(fixture.request(assertion))

        then()
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.ChallengeWindowExpired)
        coVerify(exactly = 0) { fixture.passKeyDataSource.markAsUsed(any()) }
    }

    @Test
    fun `should take the challenge once, so a replayed assertion finds none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubKnownPasskey()
        coEvery { fixture.passKeyDataSource.takeCachedChallenge(fixture.requestId) } returnsMany
            listOf(fixture.assertionRequest.toJson(), null)
        val assertion = fixture.authenticator.assertion(fixture.challenge, fixture.userHandle)

        whenn()
        val first = fixture.sut.invoke(fixture.request(assertion))
        val replay = fixture.sut.invoke(fixture.request(assertion))

        then()
        assertTrue(first.isSuccess)
        assertTrue(replay.exceptionOrNull() is FinishAssertion.Error.ChallengeWindowExpired)
        coVerify(exactly = 1) { fixture.passKeyDataSource.markAsUsed(any()) }
    }

    @Test
    fun `should return PasskeyOwnerNotFound when no account knows the passkey`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubKnownPasskey()
        every { fixture.credentialRepository.lookup(any(), any()) } returns Optional.empty()
        val assertion = fixture.authenticator.assertion(fixture.challenge, fixture.userHandle)

        whenn()
        val result = fixture.sut.invoke(fixture.request(assertion))

        then()
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.PasskeyOwnerNotFound)
        coVerify(exactly = 0) { fixture.passKeyDataSource.markAsUsed(any()) }
    }

    @Test
    fun `should reject an assertion signed by another key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubKnownPasskey()
        val assertion = fixture.authenticator.assertion(
            challenge = fixture.challenge,
            userHandle = fixture.userHandle,
            signingKey = TestPasskeyAuthenticator.newKeyPair(),
        )

        whenn()
        val result = fixture.sut.invoke(fixture.request(assertion))

        then()
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.PasskeyOwnerNotFound)
        coVerify(exactly = 0) { fixture.passKeyDataSource.markAsUsed(any()) }
    }

    @Test
    fun `should reject an assertion signed for another sign-in attempt`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubKnownPasskey()
        val otherChallenge = TestPasskeyAuthenticator.encode(ByteArray(32) { 7 })
        val assertion = fixture.authenticator.assertion(otherChallenge, fixture.userHandle)

        whenn()
        val result = fixture.sut.invoke(fixture.request(assertion))

        then()
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.PasskeyOwnerNotFound)
        coVerify(exactly = 0) { fixture.passKeyDataSource.markAsUsed(any()) }
    }

    @Test
    fun `should reject an assertion made for another origin`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubKnownPasskey()
        val assertion = fixture.authenticator.assertion(
            challenge = fixture.challenge,
            userHandle = fixture.userHandle,
            origin = "https://phishing.example",
        )

        whenn()
        val result = fixture.sut.invoke(fixture.request(assertion))

        then()
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.PasskeyOwnerNotFound)
        coVerify(exactly = 0) { fixture.passKeyDataSource.markAsUsed(any()) }
    }

    @Test
    fun `should return PasskeyOwnerNotFound when the verified passkey is no longer stored`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubKnownPasskey()
        coEvery { fixture.passKeyDataSource.getCredentialBy(any<Uuid>(), any()) } returns null
        val assertion = fixture.authenticator.assertion(fixture.challenge, fixture.userHandle)

        whenn()
        val result = fixture.sut.invoke(fixture.request(assertion))

        then()
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.PasskeyOwnerNotFound)
        coVerify(exactly = 0) { fixture.passKeyDataSource.markAsUsed(any()) }
    }

    @Test
    fun `should return PasskeyOwnerNotFound when the assertion names no user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubKnownPasskey()
        val assertion = fixture.authenticator.assertion(fixture.challenge, userHandle = null)

        whenn()
        val result = fixture.sut.invoke(fixture.request(assertion))

        then()
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.PasskeyOwnerNotFound)
        coVerify(exactly = 0) { fixture.passKeyDataSource.markAsUsed(any()) }
    }

    @Test
    fun `should return VerificationFailed for a malformed assertion`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubKnownPasskey()

        whenn()
        val result = fixture.sut.invoke(fixture.request("""{"type":"public-key"}"""))

        then()
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.VerificationFailed)
        coVerify(exactly = 0) { fixture.passKeyDataSource.markAsUsed(any()) }
    }

    @Test
    fun `should return VerificationFailed when the passkey lookup itself fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stubKnownPasskey()
        every { fixture.credentialRepository.lookup(any(), any()) } throws IllegalStateException("cache down")
        val assertion = fixture.authenticator.assertion(fixture.challenge, fixture.userHandle)

        whenn()
        val result = fixture.sut.invoke(fixture.request(assertion))

        then()
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.VerificationFailed)
    }
}
