package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.authentication.entity.LockedAccount
import com.bookk.auth.domain.api.delete_account.entity.PendingAccountDeletion
import com.bookk.auth.domain.api.delete_account.operation.DeleteAccount
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
import kotlinx.coroutines.CancellationException
import library.actionconfirmation.ActionConfirmationGrant
import library.actionconfirmation.test.ActionConfirmationTestTokens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DeleteAccountImplTest {

    private class SutFixture {
        val userId = Uuid.random()
        val authId = Uuid.random()
        val confirmationTokens = ActionConfirmationTestTokens()
        val accountDataSource = mockk<AccountDataSource>()
        val accountDeletionDataSource = mockk<AccountDeletionDataSource>()
        val eventProducer = mockk<StandardEventProducer>()
        val transactionManager = mockk<TransactionManager>()
        val calls = mutableListOf<String>()
        val tokenSpentWhenLocking = mutableListOf<Boolean>()
        val scheduled = mutableListOf<Pair<Instant, Instant>>()
        val events = mutableListOf<AuthEvent.AccountDeletionScheduled>()
        val sentInTransaction = mutableListOf<Boolean>()
        val sut =
            DeleteAccountImpl(
                confirmationTokens.gate,
                accountDataSource,
                accountDeletionDataSource,
                eventProducer,
                transactionManager,
            )

        init {
            transactionManager.mockTransaction()
            coEvery { accountDataSource.lockAccountOfUser(userId) } coAnswers {
                calls += "lock"
                tokenSpentWhenLocking += confirmationTokens.isSpent(TOKEN_ID)
                LockedAccount(authId, deletionHold = false)
            }
            coEvery { accountDeletionDataSource.get(authId) } coAnswers {
                calls += "get"
                null
            }
            coEvery { accountDeletionDataSource.schedule(authId, any(), any()) } coAnswers {
                calls += "schedule"
                scheduled += secondArg<Instant>() to thirdArg<Instant>()
            }
            coEvery { eventProducer.send(any<AuthEvent.AccountDeletionScheduled>(), any()) } coAnswers {
                events += firstArg<AuthEvent.AccountDeletionScheduled>()
                sentInTransaction += transactionManager.isInTransaction()
            }
        }

        fun token(grant: ActionConfirmationGrant = ActionConfirmationGrant.accountDeletion(userId)): String {
            return confirmationTokens.tokenFor(grant, tokenId = TOKEN_ID)
        }
    }

    @Test
    fun `should schedule the deletion 7 days ahead and tell the user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val startedAt = Clock.System.now()

        whenn()
        val result = fixture.sut(fixture.userId, fixture.token())

        then()
        val finishedAt = Clock.System.now()
        val deletesAt = result.getOrThrow().deletesAt
        assertTrue(deletesAt in (startedAt + 7.days)..(finishedAt + 7.days))
        val (requestedAt, scheduledDeletesAt) = fixture.scheduled.single()
        assertEquals(requestedAt + 7.days, scheduledDeletesAt)
        assertEquals(deletesAt, scheduledDeletesAt)
        val event = fixture.events.single()
        assertEquals(fixture.userId, event.userId)
        assertEquals(deletesAt, event.deletesAt)
        assertFalse(event.reminder)
        assertEquals(listOf(true), fixture.sentInTransaction)
        assertEquals(listOf(true), fixture.tokenSpentWhenLocking)
        assertTrue(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should lock the account before reading its pending deletion`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut(fixture.userId, fixture.token())

        then()
        assertEquals(listOf("lock", "get", "schedule"), fixture.calls)
    }

    @Test
    fun `should not erase anything when the deletion is requested`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut(fixture.userId, fixture.token())

        then()
        coVerify(exactly = 0) { fixture.accountDataSource.deleteAuthorization(any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AuthEvent.UserDeleted>(), any()) }
    }

    @Test
    fun `should return the pending date without rescheduling or notifying again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val pending = PendingAccountDeletion.stub(authId = fixture.authId, userId = fixture.userId)
        coEvery { fixture.accountDeletionDataSource.get(fixture.authId) } returns pending

        whenn()
        val result = fixture.sut(fixture.userId, fixture.token())

        then()
        assertEquals(pending.deletesAt, result.getOrThrow().deletesAt)
        coVerify(exactly = 0) { fixture.accountDeletionDataSource.schedule(any(), any(), any()) }
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun `should refuse the deletion of an account on hold and release the token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.accountDataSource.lockAccountOfUser(fixture.userId) } returns
            LockedAccount(fixture.authId, deletionHold = true)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.token())

        then()
        assertTrue(result.exceptionOrNull() is DeleteAccount.Error.DeletionOnHold)
        coVerify(exactly = 0) { fixture.accountDeletionDataSource.schedule(any(), any(), any()) }
        assertTrue(fixture.events.isEmpty())
        assertFalse(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should refuse mismatched action confirmation claims before opening a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val grant = ActionConfirmationGrant.accountDeletion(fixture.userId)
        val wrongGrants = listOf(
            grant.copy(userId = Uuid.random()),
            grant.copy(targetId = Uuid.random()),
            grant.copy(businessId = Uuid.random()),
            ActionConfirmationGrant.personalDataExport(fixture.userId),
        )

        whenn()
        val results = wrongGrants.map { fixture.sut(fixture.userId, fixture.token(it)) }

        then()
        results.forEach { assertTrue(it.exceptionOrNull() is DeleteAccount.Error.ActionConfirmationInvalid) }
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
        coVerify(exactly = 0) { fixture.accountDataSource.lockAccountOfUser(any()) }
        assertTrue(fixture.events.isEmpty())
        assertFalse(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should refuse missing malformed and expired action confirmation tokens`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val expired = fixture.confirmationTokens.tokenFor(
            ActionConfirmationGrant.accountDeletion(fixture.userId),
            expiresIn = (-1).seconds,
        )

        whenn()
        val results = listOf("", "not-a-token", expired).map { fixture.sut(fixture.userId, it) }

        then()
        results.forEach { assertTrue(it.exceptionOrNull() is DeleteAccount.Error.ActionConfirmationInvalid) }
        coVerify(exactly = 0) { fixture.accountDataSource.lockAccountOfUser(any()) }
        coVerify(exactly = 0) { fixture.accountDeletionDataSource.schedule(any(), any(), any()) }
    }

    @Test
    fun `should refuse an already used action confirmation token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = fixture.token()
        fixture.sut(fixture.userId, token).getOrThrow()

        whenn()
        val result = fixture.sut(fixture.userId, token)

        then()
        assertTrue(result.exceptionOrNull() is DeleteAccount.Error.ActionConfirmationInvalid)
        coVerify(exactly = 1) { fixture.accountDeletionDataSource.schedule(fixture.authId, any(), any()) }
        assertEquals(1, fixture.events.size)
        assertTrue(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should return InvalidCredentials and release the token when the account is missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.accountDataSource.lockAccountOfUser(fixture.userId) } returns null

        whenn()
        val result = fixture.sut(fixture.userId, fixture.token())

        then()
        assertTrue(result.exceptionOrNull() is DeleteAccount.Error.InvalidCredentials)
        assertFalse(fixture.confirmationTokens.isSpent(TOKEN_ID))
        coVerify(exactly = 0) { fixture.accountDeletionDataSource.schedule(any(), any(), any()) }
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun `should allow retrying with the same token after a transaction fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = fixture.token()
        val failure = IllegalStateException("scheduling failed")
        coEvery { fixture.accountDeletionDataSource.schedule(fixture.authId, any(), any()) } throws failure

        whenn()
        val failed = fixture.sut(fixture.userId, token)
        val spentAfterFailure = fixture.confirmationTokens.isSpent(TOKEN_ID)
        coEvery { fixture.accountDeletionDataSource.schedule(fixture.authId, any(), any()) } returns Unit
        val retried = fixture.sut(fixture.userId, token)

        then()
        assertEquals(failure, failed.exceptionOrNull())
        assertFalse(spentAfterFailure)
        assertTrue(retried.isSuccess)
        assertTrue(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should release the token and propagate cancellation`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.accountDataSource.lockAccountOfUser(fixture.userId) } throws
            CancellationException("request cancelled")

        whenn()
        val result = runCatching { fixture.sut(fixture.userId, fixture.token()) }

        then()
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertFalse(fixture.confirmationTokens.isSpent(TOKEN_ID))
        coVerify(exactly = 0) { fixture.accountDeletionDataSource.schedule(any(), any(), any()) }
    }

    private companion object {
        const val TOKEN_ID = "token-id"
    }
}
