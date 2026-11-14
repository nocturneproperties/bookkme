package com.bookk.business.domain.impl.operation.export

import com.bookk.business.domain.api.export.StartBusinessDataExport
import com.bookk.business.domain.datasource.BusinessDataSource
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
import library.actionconfirmation.ActionConfirmationGrant
import library.actionconfirmation.test.ActionConfirmationTestTokens
import library.export.DataExport
import library.export.DataExports
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class StartBusinessDataExportImplTest {

    private class SutFixture {
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val confirmationTokens = ActionConfirmationTestTokens()
        val dataExports = mockk<DataExports>()
        val businessDataSource = mockk<BusinessDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut =
            StartBusinessDataExportImpl(confirmationTokens.gate, dataExports, businessDataSource, transactionManager)
        val started = DataExport.stub()
        val startedInTransaction = mutableListOf<Boolean>()

        init {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.isOwner(userId, businessId) } returns true
            coEvery { dataExports.start(businessId) } coAnswers {
                startedInTransaction += transactionManager.isInTransaction()
                started
            }
        }

        fun token(
            grant: ActionConfirmationGrant = ActionConfirmationGrant.businessDataExport(
                userId,
                businessId,
            ),
        ) = confirmationTokens.tokenFor(grant, tokenId = TOKEN_ID)

        fun notOwner() {
            coEvery { businessDataSource.isOwner(userId, businessId) } returns false
        }
    }

    @Test
    fun `should start the business export for its owner once confirmed with a passkey`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, fixture.token())

        then()
        assertEquals(fixture.started, result.getOrNull())
        assertEquals(listOf(true), fixture.startedInTransaction)
        assertTrue(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should check the owner before spending the token and again after locking the export`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut(fixture.userId, fixture.businessId, fixture.token())

        then()
        coVerifyOrder {
            fixture.businessDataSource.isOwner(fixture.userId, fixture.businessId)
            fixture.dataExports.start(fixture.businessId)
            fixture.businessDataSource.isOwner(fixture.userId, fixture.businessId)
        }
    }

    @Test
    fun `should not let anyone but the owner export the business, without spending their token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.notOwner()

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, fixture.token())

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.dataExports.start(any()) }
        assertFalse(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should not tell a non-owner whether the business exported recently`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.notOwner()
        coEvery { fixture.dataExports.start(fixture.businessId) } throws DataExports.Error.TooFrequent()

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, fixture.token())

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should refuse an action confirmation token issued for another business`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(
            fixture.userId,
            fixture.businessId,
            fixture.token(ActionConfirmationGrant.businessDataExport(fixture.userId, Uuid.random())),
        )

        then()
        assertTrue(result.exceptionOrNull() is StartBusinessDataExport.Error.ActionConfirmationInvalid)
        coVerify(exactly = 0) { fixture.dataExports.start(any()) }
    }

    @Test
    fun `should refuse an action confirmation token issued for the personal export`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(
            fixture.userId,
            fixture.businessId,
            fixture.token(ActionConfirmationGrant.personalDataExport(fixture.userId)),
        )

        then()
        assertTrue(result.exceptionOrNull() is StartBusinessDataExport.Error.ActionConfirmationInvalid)
    }

    @Test
    fun `should refuse an action confirmation token that was already used`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = fixture.token()
        fixture.sut(fixture.userId, fixture.businessId, token)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, token)

        then()
        assertTrue(result.exceptionOrNull() is StartBusinessDataExport.Error.ActionConfirmationInvalid)
        coVerify(exactly = 1) { fixture.dataExports.start(fixture.businessId) }
    }

    @Test
    fun `should refuse a second export within a day and give the token back`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.dataExports.start(fixture.businessId) } throws DataExports.Error.TooFrequent()

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, fixture.token())

        then()
        assertTrue(result.exceptionOrNull() is DataExports.Error.TooFrequent)
        assertFalse(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should refuse when the caller stopped owning the business while waiting for the lock`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.businessDataSource.isOwner(fixture.userId, fixture.businessId) } returnsMany
            listOf(true, false)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, fixture.token())

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        assertFalse(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    private companion object {
        const val TOKEN_ID = "token-id"
    }
}
