package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.toRemote
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.Page
import com.bookk.core.domain.entity.PageCursor
import com.bookk.core.domain.entity.PageError
import com.bookk.core.domain.entity.PageRequest
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GetClientsImplTest {

    private val requestUserId = Uuid.random()

    private class SutFixture {
        val clientDataSource = mockk<ClientDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetClientsImpl(clientDataSource, businessPermissionDataSource, transactionManager)

        fun grantPermission(permission: ResourcePermission) {
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.CLIENTS) } returns
                permission
        }
    }

    @Test
    fun `should return a page of clients with the next cursor`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clients = listOf(Client.Detached.stub(), Client.Integrated.stub())
        val next = PageCursor(after = Instant.fromEpochSeconds(1_800_000_000), id = clients.last().id)
        with(fixture) {
            transactionManager.mockTransaction()
            grantPermission(ResourcePermission.FULL)
            coEvery { clientDataSource.getClientsPage(businessId, any(), null) } returns Page(clients, next)
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, limit = null, cursor = null, query = null)

        then()
        val page = result.getOrThrow()
        assertEquals(clients.map { it.toRemote() }.map { it.id }, page.data.map { it.id })
        assertEquals(clients.map { it.toRemote() }.map { it.userId }, page.data.map { it.userId })
        assertEquals(next.encode(), page.nextCursor)
    }

    @Test
    fun `should read the page after the cursor with the requested limit and query`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val cursor = PageCursor(after = Instant.fromEpochSeconds(1_800_000_000), id = Uuid.random())
        with(fixture) {
            transactionManager.mockTransaction()
            grantPermission(ResourcePermission(view = true, update = false, delete = false))
            coEvery { clientDataSource.getClientsPage(businessId, any(), "alice") } returns
                Page(emptyList(), next = null)
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, limit = 20, cursor = cursor.encode(), query = "alice")

        then()
        assertNull(result.getOrThrow().nextCursor)
        coVerify(exactly = 1) {
            fixture.clientDataSource.getClientsPage(
                businessId,
                match<PageRequest> { it.limit == 20 && it.after == cursor },
                "alice",
            )
        }
    }

    @Test
    fun `should reject an invalid limit without opening a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(requestUserId, Uuid.random(), limit = 101, cursor = null, query = null)

        then()
        assertTrue(result.exceptionOrNull() is PageError.LimitInvalid)
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should reject an invalid cursor without opening a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(requestUserId, Uuid.random(), limit = null, cursor = "garbage", query = null)

        then()
        assertTrue(result.exceptionOrNull() is PageError.CursorInvalid)
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should return failure when user has no permission for the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            grantPermission(ResourcePermission.NONE)
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, limit = null, cursor = null, query = null)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.clientDataSource.getClientsPage(any(), any(), any()) }
    }

    @Test
    fun `should return failure when the caller is a suspended employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.CLIENTS) } throws
                EmployeeAccessSuspended()
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, limit = null, cursor = null, query = null)

        then()
        assertTrue(result.exceptionOrNull() is EmployeeAccessSuspended)
        coVerify(exactly = 0) { fixture.clientDataSource.getClientsPage(any(), any(), any()) }
    }

    @Test
    fun `should assert permission against the requested business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            grantPermission(ResourcePermission.FULL)
            coEvery { clientDataSource.getClientsPage(businessId, any(), null) } returns Page(emptyList(), null)
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, limit = null, cursor = null, query = null)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS)
        }
    }
}
