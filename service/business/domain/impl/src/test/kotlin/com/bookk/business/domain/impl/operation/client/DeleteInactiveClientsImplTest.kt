package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.ErasedClientDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.business.client.api.event.BusinessEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DeleteInactiveClientsImplTest {

    private class SutFixture {
        val businessDataSource = mockk<BusinessDataSource>()
        val clientDataSource = mockk<ClientDataSource>()
        val erasedClientDataSource = mockk<ErasedClientDataSource>()
        val recorded = mutableListOf<Pair<Uuid, Uuid>>()
        val recordedInTransaction = mutableListOf<Boolean>()
        val transactionManager = mockk<TransactionManager>()
        val eventProducer = mockk<StandardEventProducer>()
        val sentEvents = mutableListOf<BusinessEvent.ClientDeleted>()
        val sentInTransaction = mutableListOf<Boolean>()
        val sut =
            DeleteInactiveClientsImpl(
                businessDataSource,
                clientDataSource,
                erasedClientDataSource,
                transactionManager,
                eventProducer,
            )

        init {
            transactionManager.mockTransaction()
            coEvery { erasedClientDataSource.record(any(), any()) } coAnswers {
                recorded += firstArg<Uuid>() to secondArg<Uuid>()
                recordedInTransaction += transactionManager.isInTransaction()
            }
            coEvery { eventProducer.send(any<BusinessEvent.ClientDeleted>(), any()) } coAnswers {
                sentEvents += firstArg<BusinessEvent.ClientDeleted>()
                sentInTransaction += transactionManager.isInTransaction()
            }
        }
    }

    private fun Instant.monthsEarlier(months: Int) = minus(months, DateTimeUnit.MONTH, TimeZone.UTC)

    @Test
    fun `should delete each business's clients inactive for longer than its retention period`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val shortRetentionId = Uuid.random()
        val longRetentionId = Uuid.random()
        val shortCutoff = slot<Instant>()
        val longCutoff = slot<Instant>()
        with(fixture) {
            coEvery { businessDataSource.getClientDataRetentionMonths() } returns
                mapOf(shortRetentionId to 12, longRetentionId to 120)
            coEvery { clientDataSource.deleteInactiveClients(shortRetentionId, capture(shortCutoff)) } returns
                emptyList()
            coEvery { clientDataSource.deleteInactiveClients(longRetentionId, capture(longCutoff)) } returns emptyList()
        }
        val startedAt = Clock.System.now()

        whenn()
        val result = fixture.sut()

        then()
        val finishedAt = Clock.System.now()
        assertTrue(result.isSuccess)
        assertTrue(shortCutoff.captured in startedAt.monthsEarlier(12)..finishedAt.monthsEarlier(12))
        assertTrue(longCutoff.captured in startedAt.monthsEarlier(120)..finishedAt.monthsEarlier(120))
    }

    @Test
    fun `should announce every deleted client inside the transaction that deleted it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val deletedIds = listOf(Uuid.random(), Uuid.random())
        with(fixture) {
            coEvery { businessDataSource.getClientDataRetentionMonths() } returns mapOf(businessId to 36)
            coEvery { clientDataSource.deleteInactiveClients(businessId, any()) } returns deletedIds
        }

        whenn()
        fixture.sut()

        then()
        assertEquals(deletedIds, fixture.sentEvents.map { it.clientId })
        assertTrue(fixture.sentEvents.all { it.businessId == businessId })
        assertEquals(listOf(true, true), fixture.sentInTransaction)
    }

    @Test
    fun `should record every deleted client inside the transaction so a restore can run it again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val deletedIds = listOf(Uuid.random(), Uuid.random())
        with(fixture) {
            coEvery { businessDataSource.getClientDataRetentionMonths() } returns mapOf(businessId to 36)
            coEvery { clientDataSource.deleteInactiveClients(businessId, any()) } returns deletedIds
        }

        whenn()
        fixture.sut()

        then()
        assertEquals(deletedIds.map { businessId to it }, fixture.recorded)
        assertEquals(listOf(true, true), fixture.recordedInTransaction)
    }

    @Test
    fun `should delete each business in its own read committed transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            coEvery { businessDataSource.getClientDataRetentionMonths() } returns
                mapOf(Uuid.random() to 36, Uuid.random() to 36)
            coEvery { clientDataSource.deleteInactiveClients(any(), any()) } returns emptyList()
        }

        whenn()
        fixture.sut()

        then()
        coVerify(exactly = 2) {
            fixture.transactionManager.transaction<Any>(TransactionIsolation.READ_COMMITTED, any())
        }
    }

    @Test
    fun `should still delete for the other businesses when one fails and then report the failure`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val failingId = Uuid.random()
        val otherId = Uuid.random()
        val error = IllegalStateException("deletion failed")
        with(fixture) {
            coEvery { businessDataSource.getClientDataRetentionMonths() } returns
                linkedMapOf(failingId to 36, otherId to 36)
            coEvery { clientDataSource.deleteInactiveClients(failingId, any()) } throws error
            coEvery { clientDataSource.deleteInactiveClients(otherId, any()) } returns emptyList()
        }

        whenn()
        val result = fixture.sut()

        then()
        assertSame(error, result.exceptionOrNull())
        coVerify(exactly = 1) { fixture.clientDataSource.deleteInactiveClients(otherId, any()) }
    }

    @Test
    fun `should report the first failure with every later one suppressed on it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val firstId = Uuid.random()
        val secondId = Uuid.random()
        val firstError = IllegalStateException("first deletion failed")
        val secondError = IllegalStateException("second deletion failed")
        with(fixture) {
            coEvery { businessDataSource.getClientDataRetentionMonths() } returns
                linkedMapOf(firstId to 36, secondId to 36)
            coEvery { clientDataSource.deleteInactiveClients(firstId, any()) } throws firstError
            coEvery { clientDataSource.deleteInactiveClients(secondId, any()) } throws secondError
        }

        whenn()
        val result = fixture.sut()

        then()
        assertSame(firstError, result.exceptionOrNull())
        assertEquals(listOf(secondError), firstError.suppressed.toList())
    }

    @Test
    fun `should announce no client of a business whose deletion failed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val failingId = Uuid.random()
        val otherId = Uuid.random()
        val otherDeleted = Uuid.random()
        with(fixture) {
            coEvery { businessDataSource.getClientDataRetentionMonths() } returns
                linkedMapOf(failingId to 36, otherId to 36)
            coEvery { clientDataSource.deleteInactiveClients(failingId, any()) } throws
                IllegalStateException("deletion failed")
            coEvery { clientDataSource.deleteInactiveClients(otherId, any()) } returns listOf(otherDeleted)
        }

        whenn()
        fixture.sut()

        then()
        assertEquals(listOf(otherId to otherDeleted), fixture.sentEvents.map { it.businessId to it.clientId })
    }

    @Test
    fun `should fail when the retention periods cannot be read`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val error = IllegalStateException("database down")
        coEvery { fixture.businessDataSource.getClientDataRetentionMonths() } throws error

        whenn()
        val result = fixture.sut()

        then()
        assertSame(error, result.exceptionOrNull())
        coVerify(exactly = 0) { fixture.clientDataSource.deleteInactiveClients(any(), any()) }
    }
}
