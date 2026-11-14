package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import library.encryption.test.ShredRecordingSubjectKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeleteBusinessAppointmentDataImplTest {

    private class SutFixture {
        val dataSource = mockk<AppointmentSubscriptionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val subjectKeys = ShredRecordingSubjectKeys(transactionManager::isInTransaction)
        val sut = DeleteBusinessAppointmentDataImpl(dataSource, subjectKeys, transactionManager)

        fun stubErasure(businessId: Uuid, clientIds: Set<Uuid> = emptySet()) {
            transactionManager.mockTransaction()
            coEvery { dataSource.getClientIdsForUpdate(listOf(businessId)) } returns clientIds
            coEvery { dataSource.detachBusinesses(listOf(businessId)) } returns Unit
        }
    }

    @Test
    fun `should select the business's clients, then detach the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        fixture.stubErasure(businessId)

        whenn()
        val result = fixture.sut.invoke(businessId)

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.dataSource.getClientIdsForUpdate(listOf(businessId))
            fixture.dataSource.detachBusinesses(listOf(businessId))
        }
    }

    @Test
    fun `should return failure when detach throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        fixture.stubErasure(businessId)
        coEvery { fixture.dataSource.detachBusinesses(listOf(businessId)) } answers
            { throw Error.DatabaseError("", IllegalStateException()) }

        whenn()
        val result = fixture.sut.invoke(businessId)

        then()
        assertTrue(result.isFailure)
    }

    @Test
    fun `should shred the keys of the business's clients in the transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientIds = setOf(Uuid.random(), Uuid.random())
        fixture.stubErasure(businessId, clientIds)

        whenn()
        fixture.sut.invoke(businessId)

        then()
        assertEquals(clientIds, fixture.subjectKeys.shreddedInTransaction)
    }
}
