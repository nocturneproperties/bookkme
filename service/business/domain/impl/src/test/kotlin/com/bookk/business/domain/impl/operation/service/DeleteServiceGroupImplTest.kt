package com.bookk.business.domain.impl.operation.service

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.LockedBusiness
import com.bookk.business.domain.datasource.RowLock
import com.bookk.business.domain.datasource.ServiceDataSource
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
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeleteServiceGroupImplTest {

    private class SutFixture {
        val serviceDataSource = mockk<ServiceDataSource>(relaxed = true)
        val businessDataSource = mockk<BusinessDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DeleteServiceGroupImpl(serviceDataSource, businessDataSource, businessPermissionDataSource, transactionManager)
    }

    @Test
    fun `should return success when delete service group with valid data`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val id = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.SERVICES) } returns ResourcePermission(view = false, update = false, delete = true)
        }

        whenn()
        val result = fixture.sut(userId, businessId, id)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.serviceDataSource.deleteServiceGroup(id, businessId) }
    }

    @Test
    fun `should return failure when permission denied`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val id = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.SERVICES) } returns ResourcePermission(view = true, update = false, delete = false)
        }

        whenn()
        val result = fixture.sut(userId, businessId, id)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should lock the business exclusively before any other read`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val id = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.SERVICES) } returns ResourcePermission(view = false, update = false, delete = true)
        }

        whenn()
        val result = fixture.sut(userId, businessId, id)

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE)
            fixture.businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.SERVICES)
            fixture.serviceDataSource.deleteServiceGroup(id, businessId)
        }
    }

    @Test
    fun `should return not found when the business does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val id = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns null
        }

        whenn()
        val result = fixture.sut(userId, businessId, id)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        coVerify(exactly = 0) { fixture.serviceDataSource.deleteServiceGroup(any(), any()) }
    }
}
