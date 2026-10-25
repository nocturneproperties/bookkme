package com.bookk.business.domain.impl.operation.service

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
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
import io.mockk.mockk
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeleteServiceImplTest {

    private class SutFixture {
        val serviceDataSource = mockk<ServiceDataSource>(relaxed = true)
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DeleteServiceImpl(serviceDataSource, businessPermissionDataSource, transactionManager)
    }

    @Test
    fun `should return success when delete service with valid data`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val id = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.SERVICES) } returns ResourcePermission(view = false, update = false, delete = true)
        }

        whenn()
        val result = fixture.sut(userId, businessId, id)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.serviceDataSource.deleteService(id, businessId) }
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
            coEvery { businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.SERVICES) } returns ResourcePermission(view = true, update = false, delete = false)
        }

        whenn()
        val result = fixture.sut(userId, businessId, id)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }
}
