package com.bookk.auth.data.datasource

import com.bookk.auth.data.orm.table.AccountDeletionTable
import com.bookk.auth.data.orm.table.AuthenticationTable
import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.delete_account.entity.DeletionReminder
import com.bookk.auth.domain.api.delete_account.entity.PendingAccountDeletion
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AccountDeletionDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(AuthenticationTable, AccountDeletionTable)
        val accounts = AccountDataSourceImpl()
        val sut = AccountDeletionDataSourceImpl()

        suspend fun account(): Authentication = suspendTransaction {
            accounts.createAuthorization(Authentication.stub())
        }

        suspend fun hold(authId: Uuid) = suspendTransaction {
            AuthenticationTable.update(where = { AuthenticationTable.id eq authId }) {
                it[deletionHold] = true
            }
        }
    }

    @Test
    fun `should store a scheduled deletion without any reminder sent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()

        whenn()
        suspendTransaction { fixture.sut.schedule(account.id, REQUESTED_AT, DELETES_AT) }

        then()
        assertEquals(
            PendingAccountDeletion(account.id, account.userId, DELETES_AT, DeletionReminder.NONE, deletionHold = false),
            suspendTransaction { fixture.sut.get(account.id) },
        )
    }

    @Test
    fun `should keep the first schedule when the deletion is scheduled again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        suspendTransaction { fixture.sut.schedule(account.id, REQUESTED_AT, DELETES_AT) }

        whenn()
        val result = runCatching {
            suspendTransaction { fixture.sut.schedule(account.id, REQUESTED_AT + 1.days, DELETES_AT + 1.days) }
        }

        then()
        assertTrue(result.isSuccess)
        assertEquals(DELETES_AT, suspendTransaction { fixture.sut.get(account.id) }?.deletesAt)
    }

    @Test
    fun `should find the pending deletion by the user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        val other = fixture.account()
        suspendTransaction { fixture.sut.schedule(account.id, REQUESTED_AT, DELETES_AT) }

        whenn()
        val found = suspendTransaction { fixture.sut.getByUserId(account.userId) }
        val missing = suspendTransaction { fixture.sut.getByUserId(other.userId) }

        then()
        assertEquals(account.id, found?.authId)
        assertEquals(DELETES_AT, found?.deletesAt)
        assertNull(missing)
    }

    @Test
    fun `should list only the deletions due by the cutoff of accounts not on hold`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val due = fixture.account()
        val held = fixture.account()
        val later = fixture.account()
        suspendTransaction {
            fixture.sut.schedule(due.id, REQUESTED_AT, DELETES_AT)
            fixture.sut.schedule(held.id, REQUESTED_AT, DELETES_AT - 1.hours)
            fixture.sut.schedule(later.id, REQUESTED_AT, DELETES_AT + 1.hours)
        }
        fixture.hold(held.id)

        whenn()
        val result = suspendTransaction { fixture.sut.getDueBy(DELETES_AT) }

        then()
        assertEquals(listOf(due.id), result.map { it.authId })
    }

    @Test
    fun `should remember which reminder was sent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        suspendTransaction { fixture.sut.schedule(account.id, REQUESTED_AT, DELETES_AT) }

        whenn()
        suspendTransaction { fixture.sut.setReminder(account.id, DeletionReminder.TWO_DAYS) }

        then()
        assertEquals(DeletionReminder.TWO_DAYS, suspendTransaction { fixture.sut.get(account.id) }?.reminder)
    }

    @Test
    fun `should cancel a pending deletion once`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        suspendTransaction { fixture.sut.schedule(account.id, REQUESTED_AT, DELETES_AT) }

        whenn()
        val first = suspendTransaction { fixture.sut.cancel(account.id) }
        val second = suspendTransaction { fixture.sut.cancel(account.id) }

        then()
        assertTrue(first)
        assertFalse(second)
        assertNull(suspendTransaction { fixture.sut.get(account.id) })
    }

    @Test
    fun `should drop the pending deletion together with the account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        suspendTransaction { fixture.sut.schedule(account.id, REQUESTED_AT, DELETES_AT) }

        whenn()
        suspendTransaction { fixture.accounts.deleteAuthorization(account.id) }

        then()
        assertEquals(emptyList<PendingAccountDeletion>(), suspendTransaction { fixture.sut.getDueBy(DELETES_AT) })
    }

    private companion object {
        val REQUESTED_AT: Instant = Instant.parse("2026-11-26T10:00:00Z")
        val DELETES_AT: Instant = REQUESTED_AT + 7.days
    }
}
