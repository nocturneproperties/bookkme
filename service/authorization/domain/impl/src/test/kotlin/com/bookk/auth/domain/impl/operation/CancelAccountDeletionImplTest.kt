package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.authentication.entity.LockedAccount
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.AccountDeletionDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AuthEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class CancelAccountDeletionImplTest {

    private class SutFixture {
        val userId = Uuid.random()
        val authId = Uuid.random()
        val accountDataSource = mockk<AccountDataSource>()
        val accountDeletionDataSource = mockk<AccountDeletionDataSource>()
        val eventProducer = mockk<StandardEventProducer>()
        val transactionManager = mockk<TransactionManager>()
        val calls = mutableListOf<String>()
        val events = mutableListOf<AuthEvent.AccountDeletionCancelled>()
        val sentInTransaction = mutableListOf<Boolean>()
        val sut =
            CancelAccountDeletionImpl(accountDataSource, accountDeletionDataSource, eventProducer, transactionManager)

        init {
            transactionManager.mockTransaction()
            coEvery { accountDataSource.lockAccountOfUser(userId) } coAnswers {
                calls += "lock"
                LockedAccount(authId, deletionHold = false)
            }
            coEvery { accountDeletionDataSource.cancel(authId) } coAnswers {
                calls += "cancel"
                true
            }
            coEvery { eventProducer.send(any<AuthEvent.AccountDeletionCancelled>(), any()) } coAnswers {
                events += firstArg<AuthEvent.AccountDeletionCancelled>()
                sentInTransaction += transactionManager.isInTransaction()
            }
        }
    }

    @Test
    fun `should cancel the pending deletion and tell the user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.userId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(listOf("lock", "cancel"), fixture.calls)
        assertEquals(fixture.userId, fixture.events.single().userId)
        assertEquals(listOf(true), fixture.sentInTransaction)
    }

    @Test
    fun `should succeed without notifying when no deletion is pending`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.accountDeletionDataSource.cancel(fixture.authId) } returns false

        whenn()
        val result = fixture.sut(fixture.userId)

        then()
        assertTrue(result.isSuccess)
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun `should succeed without notifying when the account is already gone`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.accountDataSource.lockAccountOfUser(fixture.userId) } returns null

        whenn()
        val result = fixture.sut(fixture.userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.accountDeletionDataSource.cancel(any()) }
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun `should return the failure when the cancellation cannot be stored`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val failure = IllegalStateException("database down")
        coEvery { fixture.accountDeletionDataSource.cancel(fixture.authId) } throws failure

        whenn()
        val result = fixture.sut(fixture.userId)

        then()
        assertEquals(failure, result.exceptionOrNull())
        assertTrue(fixture.events.isEmpty())
    }
}
