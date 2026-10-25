package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.operation.DeleteClient
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
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

internal class DeleteClientImplTest {

    private val requestUserId = Uuid.random()

    private class SutFixture {
        val clientDataSource = mockk<ClientDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DeleteClientImpl(transactionManager, clientDataSource, businessPermissionDataSource)

        init {
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.CLIENTS) } returns ResourcePermission.FULL
        }

        fun grantPermission(permission: ResourcePermission) {
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.CLIENTS) } returns permission
        }
    }

    @Test
    fun `should return success when delete client and client exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val id = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.deleteClient(businessId, id) } returns true
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, id)

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should return failure when delete client and client not exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val id = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.deleteClient(businessId, id) } returns false
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, id)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DeleteClient.Error.NotFound)
    }

    @Test
    fun `should return failure when user has read permission only`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val id = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            grantPermission(ResourcePermission(view = true, update = false, delete = false))
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, id)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.clientDataSource.deleteClient(any(), any()) }
    }

    @Test
    fun `should return failure when user has no permission for the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val id = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            grantPermission(ResourcePermission.NONE)
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, id)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.clientDataSource.deleteClient(any(), any()) }
    }

    @Test
    fun `should assert permission against the requested business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val id = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.deleteClient(businessId, id) } returns true
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, id)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS) }
    }
}
