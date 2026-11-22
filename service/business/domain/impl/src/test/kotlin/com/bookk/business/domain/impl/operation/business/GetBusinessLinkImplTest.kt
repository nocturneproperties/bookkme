package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.BusinessLink
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.business.operation.GetBusinessLink
import com.bookk.business.domain.datasource.BusinessLinkDataSource
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
import library.permissions.EmployeeAccessSuspended
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetBusinessLinkImplTest {

    private class SutFixture {
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val businessLinkDataSource = mockk<BusinessLinkDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetBusinessLinkImpl(businessLinkDataSource, businessPermissionDataSource, transactionManager)

        fun permission(permission: ResourcePermission) {
            transactionManager.mockTransaction()
            coEvery {
                businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.BUSINESS)
            } returns permission
        }
    }

    @Test
    fun `should return the business's current link to a member who can view the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val link = BusinessLink.stub(businessId = fixture.businessId)
        with(fixture) {
            permission(ResourcePermission(view = true, update = false, delete = false))
            coEvery { businessLinkDataSource.getCurrentLink(businessId) } returns link
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId)

        then()
        assertEquals(link, result.getOrNull())
    }

    @Test
    fun `should return failure when the business has no link`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            permission(ResourcePermission(view = true, update = false, delete = false))
            coEvery { businessLinkDataSource.getCurrentLink(businessId) } returns null
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId)

        then()
        assertTrue(result.exceptionOrNull() is GetBusinessLink.Error.LinkNotFound)
    }

    @Test
    fun `should return failure when the caller cannot view the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.permission(ResourcePermission.NONE)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.businessLinkDataSource.getCurrentLink(any()) }
    }

    @Test
    fun `should return failure when the caller's employee access is suspended`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery {
                businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.BUSINESS)
            } throws EmployeeAccessSuspended()
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId)

        then()
        assertTrue(result.exceptionOrNull() is EmployeeAccessSuspended)
    }
}
