package com.bookk.auth.data.datasource

import com.bookk.auth.data.orm.table.ErasedAccountTable
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ErasedAccountDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(ErasedAccountTable)
        val sut = ErasedAccountDataSourceImpl()

        suspend fun insertReapplyRequest(userId: Uuid, erasedAt: Instant = Instant.fromEpochMilliseconds(0)) = suspendTransaction {
            ErasedAccountTable.insert {
                it[this.userId] = userId
                it[this.erasedAt] = erasedAt
                it[reapplyRequested] = true
            }
        }

        suspend fun erasedAt(userId: Uuid): Instant = suspendTransaction {
            ErasedAccountTable.select(ErasedAccountTable.erasedAt)
                .where { ErasedAccountTable.userId eq userId }
                .single()[ErasedAccountTable.erasedAt]
        }

        suspend fun recordedUserIds(): Set<Uuid> = suspendTransaction {
            ErasedAccountTable.selectAll().map { it[ErasedAccountTable.userId] }.toSet()
        }
    }

    @Test
    fun `should record an erased account without asking to erase it again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        suspendTransaction { fixture.sut.record(userId) }

        then()
        assertEquals(setOf(userId), fixture.recordedUserIds())
        assertEquals(emptyList<Uuid>(), suspendTransaction { fixture.sut.getReapplyRequested() })
    }

    @Test
    fun `should keep the first erasure time when the account is recorded again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val firstErasedAt = Instant.fromEpochMilliseconds(1_000)
        fixture.insertReapplyRequest(userId, firstErasedAt)

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.record(userId) } }

        then()
        assertTrue(result.isSuccess)
        assertEquals(firstErasedAt, fixture.erasedAt(userId))
    }

    @Test
    fun `should list only the accounts a restore asked to erase again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requested = Uuid.random()
        suspendTransaction { fixture.sut.record(Uuid.random()) }
        fixture.insertReapplyRequest(requested)

        whenn()
        val pending = suspendTransaction { fixture.sut.getReapplyRequested() }

        then()
        assertEquals(listOf(requested), pending)
    }

    @Test
    fun `should claim a requested account once and clear the request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertReapplyRequest(userId)

        whenn()
        val first = suspendTransaction { fixture.sut.claimReapply(userId) }
        val second = suspendTransaction { fixture.sut.claimReapply(userId) }

        then()
        assertTrue(first)
        assertFalse(second)
        assertEquals(emptyList<Uuid>(), suspendTransaction { fixture.sut.getReapplyRequested() })
    }

    @Test
    fun `should not claim an account no restore asked to erase again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        suspendTransaction { fixture.sut.record(userId) }

        whenn()
        val claimed = suspendTransaction { fixture.sut.claimReapply(userId) }

        then()
        assertFalse(claimed)
    }

    @Test
    fun `should delete only the records erased before the cutoff`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val expired = Uuid.random()
        val recent = Uuid.random()
        suspendTransaction {
            fixture.sut.record(expired)
            fixture.sut.record(recent)
            ErasedAccountTable.update(where = { ErasedAccountTable.userId eq expired }) { it[erasedAt] = Instant.fromEpochMilliseconds(0) }
        }

        whenn()
        suspendTransaction { fixture.sut.deleteRecordedBefore(Clock.System.now() - 60.days) }

        then()
        assertEquals(setOf(recent), fixture.recordedUserIds())
    }
}
