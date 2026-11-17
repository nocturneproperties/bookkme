package com.bookk.business.domain.impl.operation.service

import com.bookk.business.domain.api.business.entity.BusinessLimits
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.business.domain.api.service.operation.CreateServiceGroup
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class CreateServiceGroupImplTest {

    private class SutFixture {
        val serviceDataSource = mockk<ServiceDataSource> { coEvery { countServiceGroups(any()) } returns 0 }
        val businessDataSource = mockk<BusinessDataSource> {
            coEvery { lockBusiness(any(), RowLock.EXCLUSIVE) } returns LockedBusiness.stub("USD")
        }
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = CreateServiceGroupImpl(
            serviceDataSource,
            businessDataSource,
            businessPermissionDataSource,
            transactionManager,
        )
    }

    private fun createTestGroup(name: String = "Group") = ServiceGroup(
        id = Uuid.random(),
        businessId = Uuid.random(),
        name = name,
        createdAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun `should refuse a service group when the business already has its maximum of groups`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val group = ServiceGroup.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(group.businessId, RowLock.EXCLUSIVE) } returns
                LockedBusiness.stub("USD", BusinessLimits.stub(maxServiceGroups = 2))
            coEvery {
                businessPermissionDataSource.getPermission(userId, group.businessId, BusinessResource.SERVICES)
            } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { serviceDataSource.countServiceGroups(group.businessId) } returns 2
        }

        whenn()
        val result = fixture.sut(userId, group)

        then()
        assertTrue(result.exceptionOrNull() is CreateServiceGroup.Error.ServiceGroupsLimitReached)
        coVerify(exactly = 0) { fixture.serviceDataSource.createServiceGroup(any()) }
    }

    @Test
    fun `should lock the business before checking permission and counting groups`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val group = ServiceGroup.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(group.businessId, RowLock.EXCLUSIVE) } returns
                LockedBusiness.stub("USD", BusinessLimits.stub(maxServiceGroups = 2))
            coEvery {
                businessPermissionDataSource.getPermission(userId, group.businessId, BusinessResource.SERVICES)
            } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { serviceDataSource.countServiceGroups(group.businessId) } returns 1
            coEvery { serviceDataSource.createServiceGroup(group) } returns group
        }

        whenn()
        val result = fixture.sut(userId, group)

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.businessDataSource.lockBusiness(group.businessId, RowLock.EXCLUSIVE)
            fixture.businessPermissionDataSource.getPermission(userId, group.businessId, BusinessResource.SERVICES)
            fixture.serviceDataSource.countServiceGroups(group.businessId)
            fixture.serviceDataSource.createServiceGroup(group)
        }
    }

    @Test
    fun `should return not found when the business of the group does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val group = ServiceGroup.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(group.businessId, RowLock.EXCLUSIVE) } returns null
        }

        whenn()
        val result = fixture.sut(Uuid.random(), group)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should return success when create service group with valid data`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val group = createTestGroup()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery {
                businessPermissionDataSource.getPermission(userId, group.businessId, BusinessResource.SERVICES)
            } returns
                ResourcePermission(view = false, update = true, delete = false)
            coEvery { serviceDataSource.createServiceGroup(group) } returns group
        }

        whenn()
        val result = fixture.sut(userId, group)

        then()
        assertTrue(result.isSuccess)
        assertEquals(group, result.getOrNull())
    }

    @Test
    fun `should return failure when name is blank`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val group = createTestGroup(name = "")

        whenn()
        val result = fixture.sut(userId, group)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateServiceGroup.Error.ValidationError)
    }

    @Test
    fun `should return failure when service group already exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val group = createTestGroup()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery {
                businessPermissionDataSource.getPermission(userId, group.businessId, BusinessResource.SERVICES)
            } returns
                ResourcePermission(view = false, update = true, delete = false)
            coEvery { serviceDataSource.createServiceGroup(group) } throws
                Error.UniqueConstraintFailed("", RuntimeException())
        }

        whenn()
        val result = fixture.sut(userId, group)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateServiceGroup.Error.ServiceGroupExist)
    }

    @Test
    fun `should return failure when permission denied`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val group = createTestGroup()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery {
                businessPermissionDataSource.getPermission(userId, group.businessId, BusinessResource.SERVICES)
            } returns
                ResourcePermission(view = true, update = false, delete = false)
        }

        whenn()
        val result = fixture.sut(userId, group)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }
}
