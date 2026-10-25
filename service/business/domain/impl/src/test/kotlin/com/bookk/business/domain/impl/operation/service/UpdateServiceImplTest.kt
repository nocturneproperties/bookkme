package com.bookk.business.domain.impl.operation.service

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.business.domain.api.service.operation.CreateService
import com.bookk.business.domain.api.service.operation.UpdateService
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
import org.joda.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class UpdateServiceImplTest {

    private class SutFixture {
        val serviceDataSource = mockk<ServiceDataSource>()
        val businessDataSource = mockk<BusinessDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = UpdateServiceImpl(serviceDataSource, businessDataSource, businessPermissionDataSource, transactionManager)

        fun allowServiceUpdates(userId: Uuid, businessId: Uuid) {
            coEvery { businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.SERVICES) } returns
                ResourcePermission(view = false, update = true, delete = false)
        }
    }

    @Test
    fun `should return success when update service with valid data`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val service = Service.stub(price = Money.parse("USD 100"))
        with(fixture) {
            transactionManager.mockTransaction()
            allowServiceUpdates(userId, service.businessId)
            coEvery { businessDataSource.lockBusiness(service.businessId, RowLock.SHARED) } returns LockedBusiness("USD")
            coEvery { serviceDataSource.getServiceGroup(service.group.id, service.businessId) } returns service.group
            coEvery { serviceDataSource.editService(service) } returns service
        }

        whenn()
        val result = fixture.sut(userId, service)

        then()
        assertTrue(result.isSuccess)
        assertEquals(service, result.getOrNull())
    }

    @Test
    fun `should price the service in the business currency whatever currency the request carries`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val service = Service.stub(price = Money.parse("USD 100"))
        val priced = service.copy(price = Money.parse("EUR 100"))
        with(fixture) {
            transactionManager.mockTransaction()
            allowServiceUpdates(userId, service.businessId)
            coEvery { businessDataSource.lockBusiness(service.businessId, RowLock.SHARED) } returns LockedBusiness("EUR")
            coEvery { serviceDataSource.getServiceGroup(service.group.id, service.businessId) } returns service.group
            coEvery { serviceDataSource.editService(priced) } returns priced
        }

        whenn()
        val result = fixture.sut(userId, service)

        then()
        assertEquals(priced, result.getOrNull())
        coVerify(exactly = 1) { fixture.serviceDataSource.editService(priced) }
    }

    @Test
    fun `should return failure when the price has more decimals than the business currency allows`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val service = Service.stub(price = Money.parse("USD 100.50"))
        with(fixture) {
            transactionManager.mockTransaction()
            allowServiceUpdates(userId, service.businessId)
            coEvery { businessDataSource.lockBusiness(service.businessId, RowLock.SHARED) } returns LockedBusiness("JPY")
        }

        whenn()
        val result = fixture.sut(userId, service)

        then()
        assertTrue(result.exceptionOrNull() is UpdateService.Error.PricePrecisionInvalid)
        coVerify(exactly = 0) { fixture.serviceDataSource.editService(any()) }
    }

    @Test
    fun `should return not found when the business does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val service = Service.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            allowServiceUpdates(userId, service.businessId)
            coEvery { businessDataSource.lockBusiness(service.businessId, RowLock.SHARED) } returns null
        }

        whenn()
        val result = fixture.sut(userId, service)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        coVerify(exactly = 0) { fixture.businessPermissionDataSource.getPermission(any(), any(), any()) }
        coVerify(exactly = 0) { fixture.serviceDataSource.editService(any()) }
    }

    @Test
    fun `should return failure when name is blank`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val service = Service.stub(name = "")

        whenn()
        val result = fixture.sut(userId, service)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateService.Error.ValidationError)
    }

    @Test
    fun `should return failure when permission denied`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val service = Service.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(service.businessId, RowLock.SHARED) } returns LockedBusiness("USD")
            coEvery { businessPermissionDataSource.getPermission(userId, service.businessId, BusinessResource.SERVICES) } returns ResourcePermission(view = true, update = false, delete = false)
        }

        whenn()
        val result = fixture.sut(userId, service)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.serviceDataSource.getServiceGroup(any(), any()) }
        coVerify(exactly = 0) { fixture.serviceDataSource.editService(any()) }
    }

    @Test
    fun `should return failure when service already exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val service = Service.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            allowServiceUpdates(userId, service.businessId)
            coEvery { businessDataSource.lockBusiness(service.businessId, RowLock.SHARED) } returns LockedBusiness("USD")
            coEvery { serviceDataSource.getServiceGroup(service.group.id, service.businessId) } returns service.group
            coEvery { serviceDataSource.editService(service) } throws Error.UniqueConstraintFailed("", RuntimeException())
        }

        whenn()
        val result = fixture.sut(userId, service)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UpdateService.Error.ServiceExist)
    }

    @Test
    fun `should return failure when the stored business currency is not supported`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val service = Service.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            allowServiceUpdates(userId, service.businessId)
            coEvery { businessDataSource.lockBusiness(service.businessId, RowLock.SHARED) } returns LockedBusiness("usd")
        }

        whenn()
        val result = fixture.sut(userId, service)

        then()
        assertTrue(result.exceptionOrNull() is UpdateService.Error.BusinessCurrencyInvalid)
        coVerify(exactly = 0) { fixture.serviceDataSource.editService(any()) }
    }

    @Test
    fun `should return failure when the service group does not belong to the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val service = Service.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            allowServiceUpdates(userId, service.businessId)
            coEvery { businessDataSource.lockBusiness(service.businessId, RowLock.SHARED) } returns LockedBusiness("USD")
            coEvery { serviceDataSource.getServiceGroup(service.group.id, service.businessId) } returns null
        }

        whenn()
        val result = fixture.sut(userId, service)

        then()
        assertTrue(result.exceptionOrNull() is UpdateService.Error.ServiceGroupNotFound)
        coVerify(exactly = 0) { fixture.serviceDataSource.editService(any()) }
    }

    @Test
    fun `should store and return the stored service group instead of the one in the request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val storedGroup = ServiceGroup.stub(name = "Stored")
        val service = Service.stub(businessId = storedGroup.businessId, group = storedGroup.copy(name = "Sent by client"))
        val resolved = service.copy(group = storedGroup)
        with(fixture) {
            transactionManager.mockTransaction()
            allowServiceUpdates(userId, service.businessId)
            coEvery { businessDataSource.lockBusiness(service.businessId, RowLock.SHARED) } returns LockedBusiness("USD")
            coEvery { serviceDataSource.getServiceGroup(storedGroup.id, service.businessId) } returns storedGroup
            coEvery { serviceDataSource.editService(resolved) } returns resolved
        }

        whenn()
        val result = fixture.sut(userId, service)

        then()
        assertEquals(storedGroup, result.getOrNull()?.group)
        coVerify(exactly = 1) { fixture.serviceDataSource.editService(resolved) }
    }

    @Test
    fun `should lock the business before any other read`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val service = Service.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            allowServiceUpdates(userId, service.businessId)
            coEvery { businessDataSource.lockBusiness(service.businessId, RowLock.SHARED) } returns LockedBusiness("USD")
            coEvery { serviceDataSource.getServiceGroup(service.group.id, service.businessId) } returns service.group
            coEvery { serviceDataSource.editService(service) } returns service
        }

        whenn()
        val result = fixture.sut(userId, service)

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.businessDataSource.lockBusiness(service.businessId, RowLock.SHARED)
            fixture.businessPermissionDataSource.getPermission(userId, service.businessId, BusinessResource.SERVICES)
            fixture.serviceDataSource.getServiceGroup(service.group.id, service.businessId)
            fixture.serviceDataSource.editService(service)
        }
    }
}
