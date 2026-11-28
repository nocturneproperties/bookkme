package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.SaveBusiness
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.appointments.domain.datasource.SavedBusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SaveBusinessImplTest {

    private class SutFixture {
        val deletedUserDataSource = mockk<DeletedUserDataSource>()
        val savedBusinessDataSource = mockk<SavedBusinessDataSource>()
        val subscriptionDataSource = mockk<AppointmentSubscriptionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = SaveBusinessImpl(
            deletedUserDataSource,
            savedBusinessDataSource,
            subscriptionDataSource,
            transactionManager,
        )

        fun stubSave(
            userId: Uuid,
            businessId: Uuid,
            deleted: Boolean = false,
            saved: Boolean = false,
            enabled: Boolean = true,
            count: Long = 0,
        ) {
            transactionManager.mockTransaction()
            coEvery { deletedUserDataSource.isDeletedForShare(userId) } returns deleted
            coEvery { savedBusinessDataSource.isSaved(userId, businessId) } returns saved
            coEvery { subscriptionDataSource.isBusinessEnabledForShare(businessId) } returns enabled
            coEvery { savedBusinessDataSource.count(userId) } returns count
            coEvery { savedBusinessDataSource.save(userId, businessId, any()) } returns Unit
        }
    }

    @Test
    fun `should save a business that takes appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        fixture.stubSave(userId, businessId)
        val before = Clock.System.now()

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.isSuccess)
        val savedAt = mutableListOf<Instant>()
        coVerify(exactly = 1) { fixture.savedBusinessDataSource.save(userId, businessId, capture(savedAt)) }
        assertTrue(savedAt.single() in before..(before + 5.seconds))
    }

    @Test
    fun `should lock the account tombstone and the business before reading`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        fixture.stubSave(userId, businessId)

        whenn()
        fixture.sut(userId, businessId)

        then()
        coVerifyOrder {
            fixture.deletedUserDataSource.isDeletedForShare(userId)
            fixture.subscriptionDataSource.isBusinessEnabledForShare(businessId)
            fixture.savedBusinessDataSource.isSaved(userId, businessId)
            fixture.savedBusinessDataSource.count(userId)
            fixture.savedBusinessDataSource.save(userId, businessId, any())
        }
    }

    @Test
    fun `should do nothing when the business is already saved`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        fixture.stubSave(
            userId,
            businessId,
            saved = true,
            enabled = false,
            count = SaveBusiness.MAX_SAVED_BUSINESSES.toLong(),
        )

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.savedBusinessDataSource.save(any(), any(), any()) }
    }

    @Test
    fun `should refuse a business that does not take appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        fixture.stubSave(userId, businessId, enabled = false)

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.exceptionOrNull() is SaveBusiness.Error.AppointmentsNotEnabled)
        coVerify(exactly = 0) { fixture.savedBusinessDataSource.save(any(), any(), any()) }
    }

    @Test
    fun `should refuse a business once the caller saved the maximum number`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        fixture.stubSave(userId, businessId, count = SaveBusiness.MAX_SAVED_BUSINESSES.toLong())

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.exceptionOrNull() is SaveBusiness.Error.LimitReached)
        coVerify(exactly = 0) { fixture.savedBusinessDataSource.save(any(), any(), any()) }
    }

    @Test
    fun `should save the last business under the limit`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        fixture.stubSave(userId, businessId, count = SaveBusiness.MAX_SAVED_BUSINESSES.toLong() - 1)

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.savedBusinessDataSource.save(userId, businessId, any()) }
    }

    @Test
    fun `should refuse a caller whose account was deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        fixture.stubSave(userId, businessId, deleted = true)

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.subscriptionDataSource.isBusinessEnabledForShare(any()) }
        coVerify(exactly = 0) { fixture.savedBusinessDataSource.save(any(), any(), any()) }
    }
}
