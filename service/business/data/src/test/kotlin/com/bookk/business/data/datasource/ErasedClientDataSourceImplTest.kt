package com.bookk.business.data.datasource

import com.bookk.business.data.orm.table.ErasedClientTable
import com.bookk.business.domain.api.client.entity.ErasedClient
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

internal class ErasedClientDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(ErasedClientTable)
        val sut = ErasedClientDataSourceImpl()

        suspend fun insertReapplyRequest(client: ErasedClient, erasedAt: Instant = Instant.fromEpochMilliseconds(0)) = suspendTransaction {
            ErasedClientTable.insert {
                it[clientId] = client.clientId
                it[businessId] = client.businessId
                it[this.erasedAt] = erasedAt
                it[reapplyRequested] = true
            }
        }

        suspend fun erasedAt(clientId: Uuid): Instant = suspendTransaction {
            ErasedClientTable.select(ErasedClientTable.erasedAt)
                .where { ErasedClientTable.clientId eq clientId }
                .single()[ErasedClientTable.erasedAt]
        }

        suspend fun recorded(): Set<ErasedClient> = suspendTransaction {
            ErasedClientTable.selectAll().map { ErasedClient(businessId = it[ErasedClientTable.businessId], clientId = it[ErasedClientTable.clientId]) }.toSet()
        }
    }

    @Test
    fun `should record an erased client without asking to erase it again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val client = ErasedClient.stub()

        whenn()
        suspendTransaction { fixture.sut.record(client.businessId, client.clientId) }

        then()
        assertEquals(setOf(client), fixture.recorded())
        assertEquals(emptyList<ErasedClient>(), suspendTransaction { fixture.sut.getReapplyRequested() })
    }

    @Test
    fun `should keep the first erasure time when the client is recorded again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val client = ErasedClient.stub()
        val firstErasedAt = Instant.fromEpochMilliseconds(1_000)
        fixture.insertReapplyRequest(client, firstErasedAt)

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.record(client.businessId, client.clientId) } }

        then()
        assertTrue(result.isSuccess)
        assertEquals(firstErasedAt, fixture.erasedAt(client.clientId))
    }

    @Test
    fun `should list only the clients a restore asked to erase again with their business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requested = ErasedClient.stub()
        suspendTransaction { fixture.sut.record(Uuid.random(), Uuid.random()) }
        fixture.insertReapplyRequest(requested)

        whenn()
        val pending = suspendTransaction { fixture.sut.getReapplyRequested() }

        then()
        assertEquals(listOf(requested), pending)
    }

    @Test
    fun `should claim a requested client once and clear the request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val client = ErasedClient.stub()
        fixture.insertReapplyRequest(client)

        whenn()
        val first = suspendTransaction { fixture.sut.claimReapply(client.clientId) }
        val second = suspendTransaction { fixture.sut.claimReapply(client.clientId) }

        then()
        assertTrue(first)
        assertFalse(second)
        assertEquals(emptyList<ErasedClient>(), suspendTransaction { fixture.sut.getReapplyRequested() })
    }

    @Test
    fun `should not claim a client no restore asked to erase again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val client = ErasedClient.stub()
        suspendTransaction { fixture.sut.record(client.businessId, client.clientId) }

        whenn()
        val claimed = suspendTransaction { fixture.sut.claimReapply(client.clientId) }

        then()
        assertFalse(claimed)
    }

    @Test
    fun `should delete only the records erased before the cutoff`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val expired = ErasedClient.stub()
        val recent = ErasedClient.stub()
        suspendTransaction {
            fixture.sut.record(expired.businessId, expired.clientId)
            fixture.sut.record(recent.businessId, recent.clientId)
            ErasedClientTable.update(where = { ErasedClientTable.clientId eq expired.clientId }) { it[erasedAt] = Instant.fromEpochMilliseconds(0) }
        }

        whenn()
        suspendTransaction { fixture.sut.deleteRecordedBefore(Clock.System.now() - 60.days) }

        then()
        assertEquals(setOf(recent), fixture.recorded())
    }
}
