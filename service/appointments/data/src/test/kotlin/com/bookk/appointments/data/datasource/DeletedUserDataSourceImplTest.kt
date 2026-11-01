package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.table.DeletedUserTable
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeletedUserDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(DeletedUserTable)
        val sut = DeletedUserDataSourceImpl()
    }

    @Test
    fun `should report a user as deleted once they are marked`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        suspendTransaction { fixture.sut.markDeleted(userId) }
        val deleted = suspendTransaction { fixture.sut.isDeletedForShare(userId) }

        then()
        assertTrue(deleted)
    }

    @Test
    fun `should report a user who was never marked as not deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction { fixture.sut.markDeleted(Uuid.random()) }

        whenn()
        val deleted = suspendTransaction { fixture.sut.isDeletedForShare(Uuid.random()) }

        then()
        assertFalse(deleted)
    }

    @Test
    fun `should not fail marking an already deleted user again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        suspendTransaction { fixture.sut.markDeleted(userId) }

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.markDeleted(userId) } }

        then()
        assertTrue(result.isSuccess)
        assertTrue(suspendTransaction { fixture.sut.isDeletedForShare(userId) })
    }

    @Test
    fun `should keep the first deletion time when the user is marked again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        suspendTransaction { fixture.sut.markDeleted(userId) }
        val firstDeletedAt = suspendTransaction { deletedAt(userId) }
        Thread.sleep(5)

        whenn()
        suspendTransaction { fixture.sut.markDeleted(userId) }

        then()
        assertEquals(firstDeletedAt, suspendTransaction { deletedAt(userId) })
    }

    private fun deletedAt(userId: Uuid) = DeletedUserTable
        .select(DeletedUserTable.deletedAt)
        .where { DeletedUserTable.userId eq userId }
        .single()[DeletedUserTable.deletedAt]
}
