package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.operation.GetBusinessDetails
import com.bookk.business.domain.datasource.BusinessDataSource
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

internal class GetBusinessDetailsImplTest {

    private class SutFixture {
        val businessDataSource = mockk<BusinessDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetBusinessDetailsImpl(businessDataSource, transactionManager)
    }

    @Test
    fun `should return the stored business without any caller-specific info`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val business = Business.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.getBusinessById(business.id) } returns business
        }

        whenn()
        val result = fixture.sut(business.id)

        then()
        assertEquals(business, result.getOrNull())
    }

    @Test
    fun `should return failure when business does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.getBusinessById(id) } returns null
        }

        whenn()
        val result = fixture.sut(id)

        then()
        assertTrue(result.exceptionOrNull() is GetBusinessDetails.Error.NotFound)
    }
}
