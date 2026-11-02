package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.table.DeletedClientTable
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

internal class DeletedClientDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(DeletedClientTable)
        val sut = DeletedClientDataSourceImpl()
    }

    @Test
    fun `should report a client as deleted once it is marked`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()

        whenn()
        suspendTransaction { fixture.sut.markDeleted(businessId, clientId) }
        val deleted = suspendTransaction { fixture.sut.isDeletedForShare(businessId, clientId) }

        then()
        assertTrue(deleted)
    }

    @Test
    fun `should report a client that was never marked as not deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        suspendTransaction { fixture.sut.markDeleted(businessId, Uuid.random()) }

        whenn()
        val deleted = suspendTransaction { fixture.sut.isDeletedForShare(businessId, Uuid.random()) }

        then()
        assertFalse(deleted)
    }

    @Test
    fun `should not report a client deleted in another business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val clientId = Uuid.random()
        suspendTransaction { fixture.sut.markDeleted(Uuid.random(), clientId) }

        whenn()
        val deleted = suspendTransaction { fixture.sut.isDeletedForShare(Uuid.random(), clientId) }

        then()
        assertFalse(deleted)
    }

    @Test
    fun `should keep the first deletion when the client is marked again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        suspendTransaction { fixture.sut.markDeleted(businessId, clientId) }
        val firstDeletedAt = suspendTransaction { deletedAt(clientId) }
        Thread.sleep(5)

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.markDeleted(businessId, clientId) } }

        then()
        assertTrue(result.isSuccess)
        assertEquals(firstDeletedAt, suspendTransaction { deletedAt(clientId) })
    }

    private fun deletedAt(clientId: Uuid) = DeletedClientTable
        .select(DeletedClientTable.deletedAt)
        .where { DeletedClientTable.clientId eq clientId }
        .single()[DeletedClientTable.deletedAt]
}
