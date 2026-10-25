package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
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

internal class SetDashboardBusinessImplTest {

    private class SutFixture {
        val businessDataSource = mockk<BusinessDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = SetDashboardBusinessImpl(businessDataSource, businessPermissionDataSource, transactionManager)
    }

    @Test
    fun `should select the business as dashboard business when user has view access`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.BUSINESS) } returns ResourcePermission(view = true, update = false, delete = false)
            coEvery { businessDataSource.setDashboardBusiness(userId, businessId) } returns Unit
        }

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.businessDataSource.setDashboardBusiness(userId, businessId) }
    }

    @Test
    fun `should return failure when user has no access to the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.BUSINESS) } returns ResourcePermission.NONE
        }

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.businessDataSource.setDashboardBusiness(any(), any()) }
    }
}
