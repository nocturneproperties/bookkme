package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessViewer
import com.bookk.business.domain.api.business.operation.GetDashboardBusiness
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetDashboardBusinessImplTest {

    private class SutFixture {
        val businessDataSource = mockk<BusinessDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetDashboardBusinessImpl(businessDataSource, businessPermissionDataSource, transactionManager)
    }

    @Test
    fun `should return business when exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = Business.stub(name = "Name")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.getDashboardBusiness(userId) } returns business
            coEvery { businessPermissionDataSource.getViewer(userId, business.id) } returns BusinessViewer.NONE
        }

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(business, result.getOrNull())
    }

    @Test
    fun `should attach the caller's viewer to the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = Business.stub()
        val viewer = BusinessViewer.stub(employeeId = Uuid.random(), clientId = Uuid.random())
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.getDashboardBusiness(userId) } returns business
            coEvery { businessPermissionDataSource.getViewer(userId, business.id) } returns viewer
        }

        whenn()
        val result = fixture.sut(userId)

        then()
        assertEquals(business.viewedBy(viewer), result.getOrNull())
    }

    @Test
    fun `should return failure when not exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.getDashboardBusiness(userId) } returns null
        }

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GetDashboardBusiness.Error.NotFound)
    }
}
