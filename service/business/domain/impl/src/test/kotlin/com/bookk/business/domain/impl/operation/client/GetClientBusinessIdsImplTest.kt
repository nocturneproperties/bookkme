package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.datasource.ClientDataSource
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

internal class GetClientBusinessIdsImplTest {

    private class SutFixture {
        val clientDataSource = mockk<ClientDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetClientBusinessIdsImpl(clientDataSource, transactionManager)
    }

    @Test
    fun `should return business ids the user is a client of`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessIds = listOf(Uuid.random(), Uuid.random())
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getBusinessIdsByUserId(userId) } returns businessIds
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(businessIds, result.getOrNull())
    }

    @Test
    fun `should return empty list when the user has no client relations`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getBusinessIdsByUserId(userId) } returns emptyList()
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }
}
