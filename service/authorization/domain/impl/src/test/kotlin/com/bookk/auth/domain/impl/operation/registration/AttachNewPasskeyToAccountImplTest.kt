package com.bookk.auth.domain.impl.operation.registration

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.identification.entity.AddPasskeyRequest
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.auth.domain.api.registration.operation.AttachNewPasskeyToAccount
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class AttachNewPasskeyToAccountImplTest {

    private class SutFixture {
        val finishPasskeyRegistration = mockk<FinishPasskeyRegistration>()
        val accountDataSource = mockk<AccountDataSource> { coEvery { lockAccount(any()) } returns true }
        val passKeyDataSource = mockk<PassKeyDataSource> { coEvery { countPasskeys(any()) } returns 0 }
        val transactionManager = mockk<TransactionManager>()
        val sut = AttachNewPasskeyToAccountImpl(
            finishPasskeyRegistration,
            accountDataSource,
            passKeyDataSource,
            transactionManager,
        )
    }

    private fun makePasskeyCredential(authId: Uuid = Uuid.random()): PasskeyCredential {
        val now = Clock.System.now()
        return PasskeyCredential(
            id = Uuid.random(),
            authId = authId,
            authInfo = Authentication(id = Uuid.random(), userId = Uuid.random(), uuid = Uuid.random()),
            handle = Uuid.random(),
            name = "New Passkey",
            credDescriptor = PasskeyCredential.CredentialDescriptor(ByteArray(0), "public-key", emptySet()),
            publicKey = "key",
            signatureCount = 0,
            isDiscoverable = true,
            isBackupEligible = false,
            isBackedUp = false,
            attestationObject = ByteArray(0),
            clientData = "{}",
            createdAt = now,
            lastUsedAt = now,
        )
    }

    @Test
    fun `should attach passkey to account successfully`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val request = AddPasskeyRequest(requestId = "req-id", publicKeyCredentialJson = "{}")
        val passkey = makePasskeyCredential()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishPasskeyRegistration.verifyRequest(request) } returns Result.success(passkey)
            coEvery { finishPasskeyRegistration.attachOwner(authId, passkey) } returns Unit
        }

        whenn()
        val result = fixture.sut.invoke(authId, request)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.finishPasskeyRegistration.verifyRequest(request) }
        coVerify(exactly = 1) { fixture.finishPasskeyRegistration.attachOwner(authId, passkey) }
    }

    @Test
    fun `should reject a new passkey when the account already has the maximum number of passkeys`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val request = AddPasskeyRequest(requestId = "req-id", publicKeyCredentialJson = "{}")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { passKeyDataSource.countPasskeys(authId) } returns
                AttachNewPasskeyToAccount.MAX_PASSKEYS.toLong()
        }

        whenn()
        val result = fixture.sut.invoke(authId, request)

        then()
        assertTrue(result.exceptionOrNull() is AttachNewPasskeyToAccount.Error.PasskeysLimitReached)
        coVerify(exactly = 0) { fixture.finishPasskeyRegistration.attachOwner(any(), any()) }
    }

    @Test
    fun `should attach a passkey while the account is one below the passkey limit`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val request = AddPasskeyRequest(requestId = "req-id", publicKeyCredentialJson = "{}")
        val passkey = makePasskeyCredential()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { passKeyDataSource.countPasskeys(authId) } returns
                AttachNewPasskeyToAccount.MAX_PASSKEYS.toLong() - 1
            coEvery { finishPasskeyRegistration.verifyRequest(request) } returns Result.success(passkey)
            coEvery { finishPasskeyRegistration.attachOwner(authId, passkey) } returns Unit
        }

        whenn()
        val result = fixture.sut.invoke(authId, request)

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.accountDataSource.lockAccount(authId)
            fixture.passKeyDataSource.countPasskeys(authId)
            fixture.finishPasskeyRegistration.attachOwner(authId, passkey)
        }
    }

    @Test
    fun `should return not found when the account to attach a passkey to is gone`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val request = AddPasskeyRequest(requestId = "req-id", publicKeyCredentialJson = "{}")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { accountDataSource.lockAccount(authId) } returns false
        }

        whenn()
        val result = fixture.sut.invoke(authId, request)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should return failure when passkey verification fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val request = AddPasskeyRequest(requestId = "req-id", publicKeyCredentialJson = "{}")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishPasskeyRegistration.verifyRequest(request) } returns Result.failure(
                FinishPasskeyRegistration.Error.ChallengeWindowExpired(),
            )
        }

        whenn()
        val result = fixture.sut.invoke(authId, request)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is FinishPasskeyRegistration.Error.ChallengeWindowExpired)
    }

    @Test
    fun `should return failure when challenge window has expired`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val request = AddPasskeyRequest(requestId = "req-id", publicKeyCredentialJson = "{}")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishPasskeyRegistration.verifyRequest(request) } returns Result.failure(
                FinishPasskeyRegistration.Error.VerificationFailed(),
            )
        }

        whenn()
        val result = fixture.sut.invoke(authId, request)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is FinishPasskeyRegistration.Error.VerificationFailed)
    }
}
