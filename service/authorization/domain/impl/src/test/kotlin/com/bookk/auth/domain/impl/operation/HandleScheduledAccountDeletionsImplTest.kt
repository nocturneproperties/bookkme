package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.delete_account.entity.DeletionReminder
import com.bookk.auth.domain.api.delete_account.entity.PendingAccountDeletion
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
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal class HandleScheduledAccountDeletionsImplTest {

    private class SutFixture {
        val accountDataSource = mockk<AccountDataSource>()
        val accountDeletionDataSource = mockk<AccountDeletionDataSource>(relaxUnitFun = true)
        val accountEraser = mockk<AccountEraser>(relaxUnitFun = true)
        val eventProducer = mockk<StandardEventProducer>()
        val transactionManager = mockk<TransactionManager>()
        val calls = mutableListOf<String>()
        val reminders = mutableListOf<AuthEvent.AccountDeletionScheduled>()
        val sentInTransaction = mutableListOf<Boolean>()
        val sut =
            HandleScheduledAccountDeletionsImpl(
                accountDataSource,
                accountDeletionDataSource,
                accountEraser,
                eventProducer,
                transactionManager,
            )

        init {
            transactionManager.mockTransaction()
            coEvery { eventProducer.send(any<AuthEvent.AccountDeletionScheduled>(), any()) } coAnswers {
                reminders += firstArg<AuthEvent.AccountDeletionScheduled>()
                sentInTransaction += transactionManager.isInTransaction()
            }
        }

        fun pending(vararg deletions: PendingAccountDeletion) {
            coEvery { accountDeletionDataSource.getDueBy(any()) } returns deletions.toList()
            deletions.forEach { deletion ->
                coEvery { accountDataSource.lockAccount(deletion.authId) } coAnswers {
                    calls += "lock"
                    true
                }
                coEvery { accountDeletionDataSource.get(deletion.authId) } coAnswers {
                    calls += "get"
                    deletion
                }
            }
        }
    }

    private fun now(): Instant {
        return Clock.System.now()
    }

    @Test
    fun `should look for deletions due within 2 days`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val cutoff = slot<Instant>()
        coEvery { fixture.accountDeletionDataSource.getDueBy(capture(cutoff)) } returns emptyList()
        val startedAt = now()

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        assertTrue(cutoff.captured in (startedAt + 2.days)..(now() + 2.days))
    }

    @Test
    fun `should remind the user 2 days before the deletion`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deletion = PendingAccountDeletion.stub(deletesAt = now() + 47.hours)
        fixture.pending(deletion)

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        val reminder = fixture.reminders.single()
        assertEquals(deletion.userId, reminder.userId)
        assertEquals(deletion.deletesAt, reminder.deletesAt)
        assertTrue(reminder.reminder)
        coVerify(exactly = 1) {
            fixture.accountDeletionDataSource.setReminder(deletion.authId, DeletionReminder.TWO_DAYS)
        }
        assertEquals(listOf(true), fixture.sentInTransaction)
    }

    @Test
    fun `should not remind the user more than 2 days before the deletion`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.pending(PendingAccountDeletion.stub(deletesAt = now() + 49.hours))

        whenn()
        fixture.sut()

        then()
        assertTrue(fixture.reminders.isEmpty())
        coVerify(exactly = 0) { fixture.accountDeletionDataSource.setReminder(any(), any()) }
    }

    @Test
    fun `should not send the 2-day reminder twice`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.pending(PendingAccountDeletion.stub(deletesAt = now() + 36.hours, reminder = DeletionReminder.TWO_DAYS))

        whenn()
        fixture.sut()

        then()
        assertTrue(fixture.reminders.isEmpty())
        coVerify(exactly = 0) { fixture.accountDeletionDataSource.setReminder(any(), any()) }
    }

    @Test
    fun `should remind the user 1 day before the deletion`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deletion = PendingAccountDeletion.stub(deletesAt = now() + 23.hours, reminder = DeletionReminder.TWO_DAYS)
        fixture.pending(deletion)

        whenn()
        fixture.sut()

        then()
        val reminder = fixture.reminders.single()
        assertEquals(deletion.userId, reminder.userId)
        assertEquals(deletion.deletesAt, reminder.deletesAt)
        assertTrue(reminder.reminder)
        coVerify(exactly = 1) {
            fixture.accountDeletionDataSource.setReminder(deletion.authId, DeletionReminder.ONE_DAY)
        }
    }

    @Test
    fun `should send only the 1-day reminder when the 2-day one was missed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deletion = PendingAccountDeletion.stub(deletesAt = now() + 20.hours, reminder = DeletionReminder.NONE)
        fixture.pending(deletion)

        whenn()
        fixture.sut()

        then()
        assertEquals(1, fixture.reminders.size)
        coVerify(exactly = 1) {
            fixture.accountDeletionDataSource.setReminder(deletion.authId, DeletionReminder.ONE_DAY)
        }
        coVerify(exactly = 0) { fixture.accountDeletionDataSource.setReminder(any(), DeletionReminder.TWO_DAYS) }
    }

    @Test
    fun `should not send the 1-day reminder twice`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.pending(PendingAccountDeletion.stub(deletesAt = now() + 10.hours, reminder = DeletionReminder.ONE_DAY))

        whenn()
        fixture.sut()

        then()
        assertTrue(fixture.reminders.isEmpty())
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
    }

    @Test
    fun `should erase the account once the deletion is due`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deletion = PendingAccountDeletion.stub(deletesAt = now() - 1.minutes, reminder = DeletionReminder.ONE_DAY)
        fixture.pending(deletion)

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.accountEraser.erase(deletion.authId, deletion.userId) }
        assertTrue(fixture.reminders.isEmpty())
    }

    @Test
    fun `should erase a due account even when no reminder was sent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deletion = PendingAccountDeletion.stub(deletesAt = now() - 1.hours, reminder = DeletionReminder.NONE)
        fixture.pending(deletion)

        whenn()
        fixture.sut()

        then()
        coVerify(exactly = 1) { fixture.accountEraser.erase(deletion.authId, deletion.userId) }
        assertTrue(fixture.reminders.isEmpty())
    }

    @Test
    fun `should lock the account before reading its pending deletion`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.pending(PendingAccountDeletion.stub(deletesAt = now() - 1.minutes))

        whenn()
        fixture.sut()

        then()
        assertEquals(listOf("lock", "get"), fixture.calls)
    }

    @Test
    fun `should leave the account alone when the deletion was cancelled after it was listed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deletion = PendingAccountDeletion.stub(deletesAt = now() - 1.minutes)
        fixture.pending(deletion)
        coEvery { fixture.accountDeletionDataSource.get(deletion.authId) } returns null

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
        assertTrue(fixture.reminders.isEmpty())
    }

    @Test
    fun `should leave the account alone when it is gone`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deletion = PendingAccountDeletion.stub(deletesAt = now() - 1.minutes)
        fixture.pending(deletion)
        coEvery { fixture.accountDataSource.lockAccount(deletion.authId) } returns false

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.accountDeletionDataSource.get(any()) }
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
    }

    @Test
    fun `should neither remind nor erase an account whose deletion is on hold`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val held = PendingAccountDeletion.stub(deletesAt = now() - 1.minutes, deletionHold = true)
        val reminded = PendingAccountDeletion.stub(deletesAt = now() + 20.hours, deletionHold = true)
        fixture.pending(held, reminded)

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.accountDataSource.lockAccount(any()) }
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
        assertTrue(fixture.reminders.isEmpty())
    }

    @Test
    fun `should leave the account alone when its deletion was put on hold after it was listed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deletion = PendingAccountDeletion.stub(deletesAt = now() - 1.minutes)
        fixture.pending(deletion)
        coEvery { fixture.accountDeletionDataSource.get(deletion.authId) } returns deletion.copy(deletionHold = true)

        whenn()
        fixture.sut()

        then()
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
    }

    @Test
    fun `should still handle the other accounts when one fails and then report the failure`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val failing = PendingAccountDeletion.stub(deletesAt = now() - 1.minutes)
        val healthy = PendingAccountDeletion.stub(deletesAt = now() - 1.minutes)
        fixture.pending(failing, healthy)
        val failure = IllegalStateException("erasure failed")
        coEvery { fixture.accountEraser.erase(failing.authId, failing.userId) } throws failure

        whenn()
        val result = fixture.sut()

        then()
        assertSame(failure, result.exceptionOrNull())
        coVerify(exactly = 1) { fixture.accountEraser.erase(healthy.authId, healthy.userId) }
    }
}
