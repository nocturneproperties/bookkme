package com.bookk.business.domain.impl.operation.user

import com.bookk.business.domain.api.client.entity.ClientMerge
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SyncUserProfileImplTest {

    private class SutFixture {
        val clientDataSource = mockk<ClientDataSource>()
        val employeeDataSource = mockk<EmployeeDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val sut = SyncUserProfileImpl(transactionManager, clientDataSource, employeeDataSource, eventProducer)

        init {
            coEvery { clientDataSource.findClientMergesForUser(any(), any(), any(), any()) } returns emptyList()
        }
    }

    @Test
    fun `should sync profile to integrated clients when matching user exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery {
                clientDataSource.updateIntegratedClients(userId, "John", "Doe", "john@doe.com", any(), any())
            } returns 1
            coEvery {
                employeeDataSource.updateIntegratedEmployees(userId, "John", "Doe", "john@doe.com", any(), any())
            } returns 0
        }

        whenn()
        val result = fixture.sut(userId, "John", "Doe", "john@doe.com", null, Instant.fromEpochMilliseconds(1000))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.clientDataSource.updateIntegratedClients(userId, "John", "Doe", "john@doe.com", any(), any())
        }
    }

    @Test
    fun `should sync profile to employees linked to user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery {
                clientDataSource.updateIntegratedClients(userId, "John", "Doe", "john@doe.com", any(), any())
            } returns 0
            coEvery {
                employeeDataSource.updateIntegratedEmployees(userId, "John", "Doe", "john@doe.com", any(), any())
            } returns 1
        }

        whenn()
        val result = fixture.sut(userId, "John", "Doe", "john@doe.com", null, Instant.fromEpochMilliseconds(1000))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.employeeDataSource.updateIntegratedEmployees(userId, "John", "Doe", "john@doe.com", any(), any())
        }
    }

    @Test
    fun `should succeed as no-op when no integrated client or employee matches user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery {
                clientDataSource.updateIntegratedClients(userId, "John", "Doe", "john@doe.com", any(), any())
            } returns 0
            coEvery {
                employeeDataSource.updateIntegratedEmployees(userId, "John", "Doe", "john@doe.com", any(), any())
            } returns 0
        }

        whenn()
        val result = fixture.sut(userId, "John", "Doe", "john@doe.com", null, Instant.fromEpochMilliseconds(1000))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.clientDataSource.updateIntegratedClients(userId, "John", "Doe", "john@doe.com", any(), any())
        }
        coVerify(exactly = 1) {
            fixture.employeeDataSource.updateIntegratedEmployees(userId, "John", "Doe", "john@doe.com", any(), any())
        }
    }

    @Test
    fun `should merge detached clients holding the new contacts into the user's client before syncing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val updatedAt = Instant.fromEpochMilliseconds(1000)
        val merge = ClientMerge.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.findClientMergesForUser(userId, "+1555", "john@doe.com", updatedAt) } returns listOf(merge)
            coEvery { clientDataSource.deleteClient(merge.businessId, merge.fromClientId) } returns true
            coEvery { clientDataSource.updateIntegratedClients(userId, any(), any(), any(), any(), any()) } returns 1
            coEvery { employeeDataSource.updateIntegratedEmployees(userId, any(), any(), any(), any(), any()) } returns 0
        }

        whenn()
        val result = fixture.sut(userId, "John", "Doe", "john@doe.com", "+1555", updatedAt)

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.clientDataSource.deleteClient(merge.businessId, merge.fromClientId)
            fixture.eventProducer.send(
                match<BusinessEvent.ClientLinked> {
                    it.businessId == merge.businessId && it.clientId == merge.toClientId && it.userId == userId &&
                        it.mergedClientIds == listOf(merge.fromClientId)
                },
                any()
            )
            fixture.clientDataSource.updateIntegratedClients(userId, "John", "Doe", "john@doe.com", "+1555", updatedAt)
        }
    }

    @Test
    fun `should announce one link per kept client, listing every duplicate merged into it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val updatedAt = Instant.fromEpochMilliseconds(1000)
        val kept = ClientMerge.stub()
        val sameKept = ClientMerge.stub(businessId = kept.businessId, toClientId = kept.toClientId)
        val elsewhere = ClientMerge.stub()
        val linked = mutableListOf<BusinessEvent.ClientLinked>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.findClientMergesForUser(userId, any(), any(), updatedAt) } returns listOf(kept, sameKept, elsewhere)
            coEvery { clientDataSource.deleteClient(any(), any()) } returns true
            coEvery { eventProducer.send(capture(linked), any()) } returns Unit
            coEvery { clientDataSource.updateIntegratedClients(userId, any(), any(), any(), any(), any()) } returns 1
            coEvery { employeeDataSource.updateIntegratedEmployees(userId, any(), any(), any(), any(), any()) } returns 0
        }

        whenn()
        fixture.sut(userId, "John", "Doe", "john@doe.com", "+1555", updatedAt)

        then()
        assertEquals(
            setOf(
                Triple(kept.businessId, kept.toClientId, listOf(kept.fromClientId, sameKept.fromClientId)),
                Triple(elsewhere.businessId, elsewhere.toClientId, listOf(elsewhere.fromClientId))
            ),
            linked.map { Triple(it.businessId, it.clientId, it.mergedClientIds) }.toSet()
        )
    }

    @Test
    fun `should not publish a merge when no detached client holds the new contacts`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.updateIntegratedClients(userId, any(), any(), any(), any(), any()) } returns 1
            coEvery { employeeDataSource.updateIntegratedEmployees(userId, any(), any(), any(), any(), any()) } returns 0
        }

        whenn()
        fixture.sut(userId, "John", "Doe", "john@doe.com", "+1555", Instant.fromEpochMilliseconds(1000))

        then()
        coVerify(exactly = 0) { fixture.clientDataSource.deleteClient(any(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<BusinessEvent.ClientLinked>(), any()) }
    }
}
