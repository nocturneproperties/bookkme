package com.bookk.business.data.datasource

import com.bookk.business.data.orm.table.UserLimitsTable
import com.bookk.business.domain.api.user.entity.UserLimits
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class UserLimitsDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(UserLimitsTable)
        val sut = UserLimitsDataSourceImpl()

        suspend fun rowCount(userId: Uuid): Long {
            return suspendTransaction { UserLimitsTable.selectAll().where { UserLimitsTable.userId eq userId }.count() }
        }
    }

    @Test
    fun `should create the user's row with the default business limit on first lock`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        val limits = suspendTransaction { fixture.sut.lockForUser(userId) }

        then()
        assertEquals(UserLimits.stub(userId), limits)
        assertEquals(1L, fixture.rowCount(userId))
    }

    @Test
    fun `should keep a single row when the user is locked again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        suspendTransaction { fixture.sut.lockForUser(userId) }

        whenn()
        suspendTransaction { fixture.sut.lockForUser(userId) }

        then()
        assertEquals(1L, fixture.rowCount(userId))
    }

    @Test
    fun `should read the business limit an operator stored for the user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        suspendTransaction { fixture.sut.lockForUser(userId) }
        suspendTransaction {
            UserLimitsTable.update({ UserLimitsTable.userId eq userId }) { it[maxBusinesses] = 40 }
        }

        whenn()
        val limits = suspendTransaction { fixture.sut.lockForUser(userId) }

        then()
        assertEquals(40, limits.maxBusinesses)
    }

    @Test
    fun `should delete only the given user's row`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val otherUserId = Uuid.random()
        suspendTransaction {
            fixture.sut.lockForUser(userId)
            fixture.sut.lockForUser(otherUserId)
        }

        whenn()
        suspendTransaction { fixture.sut.deleteForUser(userId) }

        then()
        assertEquals(0L, fixture.rowCount(userId))
        assertEquals(1L, fixture.rowCount(otherUserId))
    }
}
