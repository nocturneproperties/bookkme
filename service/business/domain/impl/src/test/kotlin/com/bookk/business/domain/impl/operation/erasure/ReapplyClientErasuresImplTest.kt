package com.bookk.business.domain.impl.operation.erasure

import com.bookk.business.domain.api.client.entity.ErasedClient
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.ErasedClientDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.business.client.api.event.BusinessEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ReapplyClientErasuresImplTest {

    private class SutFixture {
        val erasedClientDataSource = mockk<ErasedClientDataSource>()
        val clientDataSource = mockk<ClientDataSource>()
        val eventProducer = mockk<StandardEventProducer>()
        val transactionManager = mockk<TransactionManager>().apply { mockTransaction() }
        val sentEvents = mutableListOf<BusinessEvent.ClientDeleted>()
        val sentInTransaction = mutableListOf<Boolean>()
        val sut = ReapplyClientErasuresImpl(erasedClientDataSource, clientDataSource, eventProducer, transactionManager)

        init {
            coEvery { clientDataSource.deleteClient(any(), any()) } returns true
            coEvery { eventProducer.send(any<BusinessEvent.ClientDeleted>(), any()) } coAnswers {
                sentEvents += firstArg<BusinessEvent.ClientDeleted>()
                sentInTransaction += transactionManager.isInTransaction()
            }
        }

        fun requested(vararg clients: ErasedClient) {
            coEvery { erasedClientDataSource.getReapplyRequested() } returns clients.toList()
            coEvery { erasedClientDataSource.claimReapply(any()) } returns true
        }
    }

    @Test
    fun `should delete a client a restore brought back and announce it inside the transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val client = ErasedClient.stub()
        fixture.requested(client)

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.clientDataSource.deleteClient(client.businessId, client.clientId) }
        assertEquals(listOf(client.businessId to client.clientId), fixture.sentEvents.map { it.businessId to it.clientId })
        assertEquals(listOf(true), fixture.sentInTransaction)
    }

    @Test
    fun `should still announce the deletion when the client row is already gone`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val client = ErasedClient.stub()
        fixture.requested(client)
        coEvery { fixture.clientDataSource.deleteClient(client.businessId, client.clientId) } returns false

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        assertEquals(listOf(client.clientId), fixture.sentEvents.map { it.clientId })
    }

    @Test
    fun `should leave an erasure alone when another instance already claimed it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val client = ErasedClient.stub()
        fixture.requested(client)
        coEvery { fixture.erasedClientDataSource.claimReapply(client.clientId) } returns false

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.clientDataSource.deleteClient(any(), any()) }
        assertEquals(emptyList<BusinessEvent.ClientDeleted>(), fixture.sentEvents)
    }

    @Test
    fun `should claim the erasure before deleting the client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val client = ErasedClient.stub()
        fixture.requested(client)

        whenn()
        fixture.sut()

        then()
        coVerifyOrder {
            fixture.erasedClientDataSource.claimReapply(client.clientId)
            fixture.clientDataSource.deleteClient(client.businessId, client.clientId)
        }
    }

    @Test
    fun `should succeed when no restore asked for anything`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.requested()

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.erasedClientDataSource.claimReapply(any()) }
    }

    @Test
    fun `should still erase the other clients when one fails and then report the failure`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val failing = ErasedClient.stub()
        val next = ErasedClient.stub()
        val error = RuntimeException("erasure failed")
        fixture.requested(failing, next)
        coEvery { fixture.clientDataSource.deleteClient(failing.businessId, failing.clientId) } throws error

        whenn()
        val result = fixture.sut()

        then()
        assertSame(error, result.exceptionOrNull())
        coVerify(exactly = 1) { fixture.clientDataSource.deleteClient(next.businessId, next.clientId) }
    }
}
