package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import library.encryption.test.ShredRecordingSubjectKeys
import library.export.DataExports
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeleteBusinessImplTest {

    private class SutFixture {
        val businessDataSource = mockk<BusinessDataSource>()
        val clientDataSource = mockk<ClientDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val dataExports = mockk<DataExports>()
        val subjectKeys = ShredRecordingSubjectKeys(transactionManager::isInTransaction)
        val discardedInTransaction = mutableListOf<Pair<List<Uuid>, Boolean>>()
        val sut = DeleteBusinessImpl(
            businessDataSource,
            clientDataSource,
            transactionManager,
            eventProducer,
            dataExports,
            subjectKeys,
        )

        init {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.deleteBusinesses(any()) } returns Unit
            coEvery { dataExports.discardAll(any()) } coAnswers {
                discardedInTransaction += firstArg<Collection<Uuid>>().toList() to transactionManager.isInTransaction()
            }
        }

        fun owns(userId: Uuid, businessIds: List<Uuid>, clientIds: Set<Uuid> = emptySet()) {
            coEvery { businessDataSource.getUserBusinessIdsForUpdate(userId) } returns businessIds
            coEvery { clientDataSource.getClientIdsOfBusinessesForUpdate(businessIds) } returns clientIds
        }
    }

    @Test
    fun `should delete business successfully and send events when user has businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessIds = listOf(Uuid.random(), Uuid.random())
        fixture.owns(userId, businessIds)

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.businessDataSource.deleteBusinesses(businessIds) }
        coVerify(exactly = 2) { fixture.eventProducer.send(any(), any()) }
    }

    @Test
    fun `should select the businesses and their clients before deleting the businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessIds = listOf(Uuid.random())
        fixture.owns(userId, businessIds)

        whenn()
        fixture.sut(userId)

        then()
        coVerifyOrder {
            fixture.businessDataSource.getUserBusinessIdsForUpdate(userId)
            fixture.clientDataSource.getClientIdsOfBusinessesForUpdate(businessIds)
            fixture.businessDataSource.deleteBusinesses(businessIds)
        }
    }

    @Test
    fun `should delete the data exports of all deleted businesses at once in the same transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessIds = listOf(Uuid.random(), Uuid.random())
        fixture.owns(userId, businessIds)

        whenn()
        fixture.sut(userId)

        then()
        assertEquals(listOf(businessIds to true), fixture.discardedInTransaction)
    }

    @Test
    fun `should discard no data export and send nothing when the user owns no business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.owns(userId, emptyList())

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.dataExports.discardAll(any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any(), any()) }
    }

    @Test
    fun `should shred the keys of every client deleted with the businesses in the same transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val clientIds = setOf(Uuid.random(), Uuid.random(), Uuid.random())
        fixture.owns(userId, listOf(Uuid.random(), Uuid.random()), clientIds)

        whenn()
        fixture.sut(userId)

        then()
        assertEquals(clientIds, fixture.subjectKeys.shreddedInTransaction)
    }
}
