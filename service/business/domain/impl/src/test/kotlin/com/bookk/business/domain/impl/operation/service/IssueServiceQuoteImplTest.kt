package com.bookk.business.domain.impl.operation.service

import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.operation.IssueServiceQuote
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import library.signing.TokenIssuer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class IssueServiceQuoteImplTest {

    private class SutFixture {
        val serviceDataSource = mockk<ServiceDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val tokenIssuer = mockk<TokenIssuer>()
        val sut = IssueQuoteImpl(serviceDataSource, transactionManager, tokenIssuer)
    }

    @Test
    fun `should return quote with services and signed token when all service ids exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val services = listOf(
            Service.stub(businessId = businessId),
            Service.stub(businessId = businessId)
        )
        val serviceIds = services.map { it.id }
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { serviceDataSource.getServicesByIds(serviceIds) } returns services
            coEvery { tokenIssuer.issue(any(), any()) } returns "signed-token"
        }

        whenn()
        val result = fixture.sut(businessId, serviceIds)

        then()
        assertTrue(result.isSuccess)
        val quote = result.getOrThrow()
        assertTrue(quote.services == services)
        assertNotNull(quote.token)
        assertTrue(quote.token.isNotBlank())
    }

    @Test
    fun `should return EmptyServiceList when service ids list is empty`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()

        whenn()
        val result = fixture.sut(businessId, emptyList())

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IssueServiceQuote.Error.EmptyServiceList)
    }

    @Test
    fun `should expand a repeated service id into one quote line per requested count`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val serviceX = Service.stub(businessId = businessId)
        val serviceY = Service.stub(businessId = businessId)
        val serviceIds = listOf(serviceX.id, serviceX.id, serviceX.id, serviceX.id, serviceX.id, serviceY.id)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { serviceDataSource.getServicesByIds(listOf(serviceX.id, serviceY.id)) } returns listOf(serviceX, serviceY)
            coEvery { tokenIssuer.issue(any(), any()) } returns "signed-token"
        }

        whenn()
        val result = fixture.sut(businessId, serviceIds)

        then()
        assertTrue(result.isSuccess)
        val quote = result.getOrThrow()
        assertTrue(quote.services.count { it == serviceX } == 5)
        assertTrue(quote.services.count { it == serviceY } == 1)
    }

    @Test
    fun `should return ServiceNotFound when a requested service id is missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val existingService = Service.stub(businessId = businessId)
        val missingId = Uuid.random()
        val serviceIds = listOf(existingService.id, missingId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { serviceDataSource.getServicesByIds(serviceIds) } returns listOf(existingService)
        }

        whenn()
        val result = fixture.sut(businessId, serviceIds)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IssueServiceQuote.Error.ServiceNotFound)
    }
}
