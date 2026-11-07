package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.export.ProfileExport
import com.bookk.user.domain.api.operation.StartPersonalDataExport
import com.bookk.user.domain.datasource.UserDataExportDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import library.actionconfirmation.ActionConfirmationGrant
import library.actionconfirmation.test.ActionConfirmationTestTokens
import library.export.DataExport
import library.export.DataExports
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class StartPersonalDataExportImplTest {

    private class SutFixture {
        val userId = Uuid.random()
        val confirmationTokens = ActionConfirmationTestTokens()
        val dataExports = mockk<DataExports>()
        val userDataExportDataSource = mockk<UserDataExportDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = StartPersonalDataExportImpl(confirmationTokens.gate, dataExports, userDataExportDataSource, transactionManager)
        val started = DataExport.stub()
        val startedInTransaction = mutableListOf<Boolean>()

        init {
            transactionManager.mockTransaction()
            coEvery { dataExports.start(userId) } coAnswers {
                startedInTransaction += transactionManager.isInTransaction()
                started
            }
            coEvery { userDataExportDataSource.getProfile(userId) } returns ProfileExport.stub(id = userId)
        }

        fun token(grant: ActionConfirmationGrant = ActionConfirmationGrant.personalDataExport(userId)) = confirmationTokens.tokenFor(grant, tokenId = TOKEN_ID)
    }

    @Test
    fun `should start the export once the caller confirmed it with their passkey`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.userId, fixture.token())

        then()
        assertEquals(fixture.started, result.getOrNull())
        assertEquals(listOf(true), fixture.startedInTransaction)
        assertTrue(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should lock the export before reading the profile from the database`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut(fixture.userId, fixture.token())

        then()
        coVerifyOrder {
            fixture.dataExports.start(fixture.userId)
            fixture.userDataExportDataSource.getProfile(fixture.userId)
        }
    }

    @Test
    fun `should refuse an action confirmation token issued for another action`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = fixture.token(ActionConfirmationGrant.businessDataExport(fixture.userId, Uuid.random()))

        whenn()
        val result = fixture.sut(fixture.userId, token)

        then()
        assertTrue(result.exceptionOrNull() is StartPersonalDataExport.Error.ActionConfirmationInvalid)
        coVerify(exactly = 0) { fixture.dataExports.start(any()) }
    }

    @Test
    fun `should refuse an action confirmation token issued to another user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = fixture.token(ActionConfirmationGrant.personalDataExport(Uuid.random()))

        whenn()
        val result = fixture.sut(fixture.userId, token)

        then()
        assertTrue(result.exceptionOrNull() is StartPersonalDataExport.Error.ActionConfirmationInvalid)
    }

    @Test
    fun `should refuse an action confirmation token that was already used`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = fixture.token()
        fixture.sut(fixture.userId, token)

        whenn()
        val result = fixture.sut(fixture.userId, token)

        then()
        assertTrue(result.exceptionOrNull() is StartPersonalDataExport.Error.ActionConfirmationInvalid)
        coVerify(exactly = 1) { fixture.dataExports.start(fixture.userId) }
    }

    @Test
    fun `should refuse a second export within a day and give the action confirmation token back`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.dataExports.start(fixture.userId) } throws DataExports.Error.TooFrequent()

        whenn()
        val result = fixture.sut(fixture.userId, fixture.token())

        then()
        assertTrue(result.exceptionOrNull() is DataExports.Error.TooFrequent)
        assertFalse(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should fail for a user without a profile`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.userDataExportDataSource.getProfile(fixture.userId) } returns null

        whenn()
        val result = fixture.sut(fixture.userId, fixture.token())

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        assertFalse(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    private companion object {
        const val TOKEN_ID = "token-id"
    }
}
