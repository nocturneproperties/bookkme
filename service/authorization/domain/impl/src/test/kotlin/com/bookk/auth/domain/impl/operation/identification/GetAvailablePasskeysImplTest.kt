package com.bookk.auth.domain.impl.operation.identification

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.core.AppLevelConstants
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

internal class GetAvailablePasskeysImplTest {

    private class SutFixture {
        val passKeyDataSource = mockk<PassKeyDataSource>()
        val accountDataSource = mockk<AccountDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetAvailablePasskeysImpl(passKeyDataSource, accountDataSource, transactionManager)

        val authentication = Authentication.stub()
    }

    private fun base64Url(bytes: ByteArray): String {
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
    }

    @Test
    fun `should return each passkey with its credential id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = PasskeyCredential.stub(authInfo = fixture.authentication, name = "Phone")
        val second = PasskeyCredential.stub(
            authInfo = fixture.authentication,
            credDescriptor = PasskeyCredential.CredentialDescriptor(ByteArray(16) { 9 }, "public-key", emptySet()),
            isBackedUp = true,
        )
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { accountDataSource.getAuthRecordById(authentication.id) } returns authentication
            coEvery { passKeyDataSource.getCredentialBy(authentication.id) } returns listOf(first, second)
        }

        whenn()
        val result = fixture.sut.invoke(fixture.authentication.id)

        then()
        val passkeys = result.getOrThrow().passkeys
        assertEquals(listOf(first.id, second.id), passkeys.map { it.id })
        assertEquals(first.name, passkeys[0].name)
        assertEquals(second.isBackedUp, passkeys[1].isBackedUp)
        assertEquals(
            listOf(base64Url(first.credDescriptor.id), base64Url(second.credDescriptor.id)),
            passkeys.map { it.credentialId },
        )
    }

    @Test
    fun `should return the relying party id and the account's user handle for passkey signals`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { accountDataSource.getAuthRecordById(authentication.id) } returns authentication
            coEvery { passKeyDataSource.getCredentialBy(authentication.id) } returns
                listOf(PasskeyCredential.stub(authInfo = authentication))
        }

        whenn()
        val result = fixture.sut.invoke(fixture.authentication.id)

        then()
        val list = result.getOrThrow()
        assertEquals(AppLevelConstants.domainName, list.rpId)
        assertEquals(base64Url(fixture.authentication.uuid.toByteArray()), list.userHandle)
    }

    @Test
    fun `should return the user handle and no passkeys for an account without passkeys`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { accountDataSource.getAuthRecordById(authentication.id) } returns authentication
            coEvery { passKeyDataSource.getCredentialBy(authentication.id) } returns emptyList()
        }

        whenn()
        val result = fixture.sut.invoke(fixture.authentication.id)

        then()
        val list = result.getOrThrow()
        assertEquals(emptyList<Any>(), list.passkeys)
        assertEquals(base64Url(fixture.authentication.uuid.toByteArray()), list.userHandle)
    }

    @Test
    fun `should return NotFound when the signed-in account no longer exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { accountDataSource.getAuthRecordById(authentication.id) } returns null
        }

        whenn()
        val result = fixture.sut.invoke(fixture.authentication.id)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        coVerify(exactly = 0) { fixture.passKeyDataSource.getCredentialBy(any<Uuid>()) }
    }
}
