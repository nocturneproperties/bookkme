package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetBusinessPermissionImplTest {

    private class SutFixture {
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetBusinessPermissionImpl(businessPermissionDataSource, transactionManager)
    }

    private val userId = Uuid.random()
    private val businessId = Uuid.random()

    @Test
    fun `should return the stored permission`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val permission = ResourcePermission.FULL
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.BUSINESS) } returns permission
        }

        whenn()
        val result = fixture.sut.invoke(userId, businessId, BusinessResource.BUSINESS)

        then()
        assertTrue(result.isSuccess)
        assertEquals(permission, result.getOrNull())
    }

    @Test
    fun `should return no permission when the user holds none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.BUSINESS) } returns ResourcePermission.NONE
        }

        whenn()
        val result = fixture.sut.invoke(userId, businessId, BusinessResource.BUSINESS)

        then()
        assertTrue(result.isSuccess)
        assertEquals(ResourcePermission.NONE, result.getOrNull())
    }

    @Test
    fun `should return failure when the datasource fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery {
                businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.BUSINESS)
            } throws RuntimeException("db error")
        }

        whenn()
        val result = fixture.sut.invoke(userId, businessId, BusinessResource.BUSINESS)

        then()
        assertTrue(result.isFailure)
    }
}
