package com.bookk.business.domain.impl.operation.service

import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.operation.GetServicesByIds
import com.bookk.business.domain.datasource.ServiceDataSource
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

internal class GetServicesByIdsImplTest {

    private class SutFixture {
        val serviceDataSource = mockk<ServiceDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetServicesByIdsImpl(serviceDataSource, transactionManager)
    }

    @Test
    fun `should return requested services in request order`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val first = Service.stub(businessId = businessId)
        val second = Service.stub(businessId = businessId)
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { serviceDataSource.getServicesByIds(any()) } returns listOf(first, second)
        }

        whenn()
        val result = fixture.sut.invoke(businessId, listOf(second.id, first.id))

        then()
        assertEquals(listOf(second, first), result.getOrNull())
    }

    @Test
    fun `should repeat a service requested more than once`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val service = Service.stub(businessId = businessId)
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { serviceDataSource.getServicesByIds(listOf(service.id)) } returns listOf(service)
        }

        whenn()
        val result = fixture.sut.invoke(businessId, listOf(service.id, service.id))

        then()
        assertEquals(listOf(service, service), result.getOrNull())
    }

    @Test
    fun `should return empty list without querying when no services requested`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut.invoke(Uuid.random(), emptyList())

        then()
        assertEquals(emptyList<Service>(), result.getOrNull())
        coVerify(exactly = 0) { fixture.serviceDataSource.getServicesByIds(any()) }
    }

    @Test
    fun `should return failure when a requested service does not exist`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { serviceDataSource.getServicesByIds(any()) } returns emptyList()
        }

        whenn()
        val result = fixture.sut.invoke(businessId, listOf(Uuid.random()))

        then()
        assertTrue(result.exceptionOrNull() is GetServicesByIds.Error.ServiceNotFound)
    }

    @Test
    fun `should return failure when a requested service belongs to another business`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val foreignService = Service.stub(businessId = Uuid.random())
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { serviceDataSource.getServicesByIds(listOf(foreignService.id)) } returns listOf(foreignService)
        }

        whenn()
        val result = fixture.sut.invoke(businessId, listOf(foreignService.id))

        then()
        assertTrue(result.exceptionOrNull() is GetServicesByIds.Error.ServiceNotFound)
    }
}
