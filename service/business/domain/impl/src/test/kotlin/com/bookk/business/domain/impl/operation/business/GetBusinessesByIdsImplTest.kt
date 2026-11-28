package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.business.entity.BusinessViewer
import com.bookk.business.domain.api.business.operation.GetBusinessesByIds
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetBusinessesByIdsImplTest {

    private class SutFixture {
        val businessDataSource = mockk<BusinessDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetBusinessesByIdsImpl(businessDataSource, businessPermissionDataSource, transactionManager)
    }

    @Test
    fun `should attach each business its own viewer resolved in a single lookup`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val owned = Business.stub(ownerId = userId)
        val visited = Business.stub()
        val ownedViewer = BusinessViewer.stub(permissions = BusinessPermissions.FULL, employeeId = Uuid.random())
        val visitedViewer = BusinessViewer.stub(clientId = Uuid.random())
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.getBusinessesByIds(listOf(owned.id, visited.id)) } returns
                listOf(owned, visited)
            coEvery { businessPermissionDataSource.getViewers(userId, listOf(owned.id, visited.id)) } returns
                mapOf(owned.id to ownedViewer, visited.id to visitedViewer)
        }

        whenn()
        val result = fixture.sut(listOf(owned.id, visited.id), userId)

        then()
        assertEquals(listOf(owned.viewedBy(ownedViewer), visited.viewedBy(visitedViewer)), result.getOrThrow())
        coVerify(exactly = 1) { fixture.businessPermissionDataSource.getViewers(userId, any()) }
    }

    @Test
    fun `should return businesses in the order their ids were requested`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val first = Business.stub()
        val second = Business.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.getBusinessesByIds(listOf(first.id, second.id)) } returns
                listOf(second, first)
            coEvery { businessPermissionDataSource.getViewers(userId, listOf(first.id, second.id)) } returns
                mapOf(first.id to BusinessViewer.NONE, second.id to BusinessViewer.NONE)
        }

        whenn()
        val result = fixture.sut(listOf(first.id, second.id), userId)

        then()
        assertEquals(listOf(first.id, second.id), result.getOrThrow().map { it.id })
    }

    @Test
    fun `should leave out ids that have no business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = Business.stub()
        val unknownId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.getBusinessesByIds(listOf(unknownId, business.id)) } returns listOf(business)
            coEvery { businessPermissionDataSource.getViewers(userId, listOf(business.id)) } returns
                mapOf(business.id to BusinessViewer.NONE)
        }

        whenn()
        val result = fixture.sut(listOf(unknownId, business.id), userId)

        then()
        assertEquals(listOf(business), result.getOrThrow())
    }

    @Test
    fun `should return a business requested twice once`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val first = Business.stub()
        val second = Business.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.getBusinessesByIds(listOf(first.id, second.id)) } returns
                listOf(first, second)
            coEvery { businessPermissionDataSource.getViewers(userId, listOf(first.id, second.id)) } returns
                mapOf(first.id to BusinessViewer.NONE, second.id to BusinessViewer.NONE)
        }

        whenn()
        val result = fixture.sut(listOf(first.id, second.id, first.id), userId)

        then()
        assertEquals(listOf(first.id, second.id), result.getOrThrow().map { it.id })
    }

    @Test
    fun `should return an empty list without opening a transaction when no ids are requested`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(emptyList(), Uuid.random())

        then()
        assertEquals(emptyList<Business>(), result.getOrThrow())
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should read the maximum number of ids`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val ids = List(GetBusinessesByIds.MAX_IDS) { Uuid.random() }
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.getBusinessesByIds(ids) } returns emptyList()
            coEvery { businessPermissionDataSource.getViewers(userId, emptyList()) } returns emptyMap()
        }

        whenn()
        val result = fixture.sut(ids, userId)

        then()
        assertEquals(emptyList<Business>(), result.getOrThrow())
    }

    @Test
    fun `should return failure without opening a transaction when more ids than the maximum are requested`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            val ids = List(GetBusinessesByIds.MAX_IDS + 1) { Uuid.random() }

            whenn()
            val result = fixture.sut(ids, Uuid.random())

            then()
            assertTrue(result.exceptionOrNull() is GetBusinessesByIds.Error.TooManyIds)
            coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
        }
}
