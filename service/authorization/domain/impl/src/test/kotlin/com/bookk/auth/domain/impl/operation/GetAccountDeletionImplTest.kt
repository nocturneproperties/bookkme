package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.delete_account.entity.AccountDeletionStatus
import com.bookk.auth.domain.api.delete_account.entity.PendingAccountDeletion
import com.bookk.auth.domain.datasource.AccountDeletionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GetAccountDeletionImplTest {

    private class SutFixture {
        val userId = Uuid.random()
        val accountDeletionDataSource = mockk<AccountDeletionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val readInTransaction = mutableListOf<Boolean>()
        val sut = GetAccountDeletionImpl(accountDeletionDataSource, transactionManager)

        init {
            transactionManager.mockTransaction()
        }

        fun pending(deletion: PendingAccountDeletion?) {
            coEvery { accountDeletionDataSource.getByUserId(userId) } coAnswers {
                readInTransaction += transactionManager.isInTransaction()
                deletion
            }
        }
    }

    @Test
    fun `should return the date of the pending deletion`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deletesAt = Instant.parse("2026-12-03T10:00:00Z")
        fixture.pending(PendingAccountDeletion.stub(userId = fixture.userId, deletesAt = deletesAt))

        whenn()
        val result = fixture.sut(fixture.userId)

        then()
        assertEquals(AccountDeletionStatus(deletesAt), result.getOrThrow())
        assertEquals(listOf(true), fixture.readInTransaction)
    }

    @Test
    fun `should return no date when no deletion is pending`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.pending(null)

        whenn()
        val result = fixture.sut(fixture.userId)

        then()
        assertEquals(AccountDeletionStatus(deletesAt = null), result.getOrThrow())
    }
}
