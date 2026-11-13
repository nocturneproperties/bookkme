package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.business.domain.api.business.operation.GetBusinessDetails
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.business.client.api.BusinessClient
import com.bookk.server.business.client.api.BusinessDTO
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class FillBusinessOwnersImplTest {

    private class SutFixture {
        val subscriptionDataSource = mockk<AppointmentSubscriptionDataSource>()
        val deletedUserDataSource = mockk<DeletedUserDataSource>()
        val businessClient = mockk<BusinessClient>()
        val transactionManager = mockk<TransactionManager>()
        val writesInTransaction = mutableListOf<Boolean>()
        val sut =
            FillBusinessOwnersImpl(subscriptionDataSource, deletedUserDataSource, businessClient, transactionManager)

        init {
            transactionManager.mockTransaction()
            coEvery { deletedUserDataSource.isDeletedForShare(any()) } returns false
            coEvery { subscriptionDataSource.fillMissingOwner(any(), any()) } coAnswers {
                writesInTransaction += transactionManager.isInTransaction()
            }
            coEvery { subscriptionDataSource.detachBusiness(any()) } coAnswers {
                writesInTransaction += transactionManager.isInTransaction()
            }
        }

        fun missing(vararg businessIds: Uuid) {
            coEvery { subscriptionDataSource.getBusinessIdsWithoutOwner() } returns businessIds.toList()
        }

        fun ownedBy(businessId: Uuid, ownerUserId: Uuid?) {
            coEvery { businessClient.getBusinessById(businessId) } returns
                Result.success(BusinessDTO.stub(id = businessId, ownerUserId = ownerUserId))
        }
    }

    @Test
    fun `should fill each missing owner from the business service in its own transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = Uuid.random()
        val second = Uuid.random()
        val firstOwner = Uuid.random()
        val secondOwner = Uuid.random()
        fixture.missing(first, second)
        fixture.ownedBy(first, firstOwner)
        fixture.ownedBy(second, secondOwner)

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.subscriptionDataSource.fillMissingOwner(first, firstOwner) }
        coVerify(exactly = 1) { fixture.subscriptionDataSource.fillMissingOwner(second, secondOwner) }
        assertEquals(listOf(true, true), fixture.writesInTransaction)
    }

    @Test
    fun `should check that the owner still has an account before filling it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val ownerUserId = Uuid.random()
        fixture.missing(businessId)
        fixture.ownedBy(businessId, ownerUserId)

        whenn()
        fixture.sut()

        then()
        coVerifyOrder {
            fixture.deletedUserDataSource.isDeletedForShare(ownerUserId)
            fixture.subscriptionDataSource.fillMissingOwner(businessId, ownerUserId)
        }
    }

    @Test
    fun `should drop a business whose owner already deleted their account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val ownerUserId = Uuid.random()
        fixture.missing(businessId)
        fixture.ownedBy(businessId, ownerUserId)
        coEvery { fixture.deletedUserDataSource.isDeletedForShare(ownerUserId) } returns true

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.subscriptionDataSource.detachBusiness(businessId) }
        coVerify(exactly = 0) { fixture.subscriptionDataSource.fillMissingOwner(any(), any()) }
    }

    @Test
    fun `should drop a business the business service no longer has`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val inProcess = Uuid.random()
        val overHttp = Uuid.random()
        val withoutBody = Uuid.random()
        fixture.missing(inProcess, overHttp, withoutBody)
        coEvery { fixture.businessClient.getBusinessById(inProcess) } returns
            Result.failure(GetBusinessDetails.Error.NotFound())
        coEvery { fixture.businessClient.getBusinessById(overHttp) } returns
            Result.failure(
                BusinessError(404, GetBusinessDetails.Error.NotFound().code, "Business with this id is missing"),
            )
        coEvery { fixture.businessClient.getBusinessById(withoutBody) } returns Result.failure(Error.NotFound())

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.subscriptionDataSource.detachBusiness(inProcess) }
        coVerify(exactly = 1) { fixture.subscriptionDataSource.detachBusiness(overHttp) }
        coVerify(exactly = 1) { fixture.subscriptionDataSource.detachBusiness(withoutBody) }
    }

    @Test
    fun `should leave the business alone and fail when the business service sends no owner`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        fixture.missing(businessId)
        fixture.ownedBy(businessId, ownerUserId = null)

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isFailure)
        assertTrue(fixture.writesInTransaction.isEmpty())
    }

    @Test
    fun `should still fill the other businesses and then fail when one lookup fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val unreachable = Uuid.random()
        val reachable = Uuid.random()
        val ownerUserId = Uuid.random()
        fixture.missing(unreachable, reachable)
        coEvery { fixture.businessClient.getBusinessById(unreachable) } returns
            Result.failure(Error.UnknownError(IllegalStateException("connection refused")))
        fixture.ownedBy(reachable, ownerUserId)

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isFailure)
        coVerify(exactly = 1) { fixture.subscriptionDataSource.fillMissingOwner(reachable, ownerUserId) }
        coVerify(exactly = 0) { fixture.subscriptionDataSource.detachBusiness(unreachable) }
    }

    @Test
    fun `should ask the business service nothing when every owner is known`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.missing()

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.businessClient.getBusinessById(any()) }
    }
}
