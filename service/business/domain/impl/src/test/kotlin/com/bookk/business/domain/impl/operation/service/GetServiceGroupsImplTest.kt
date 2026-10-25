package com.bookk.business.domain.impl.operation.service

import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.business.domain.datasource.ServiceDataSource
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
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GetServiceGroupsImplTest {

    private class SutFixture {
        val serviceDataSource = mockk<ServiceDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetServiceGroupsImpl(serviceDataSource, transactionManager)
    }

    @Test
    fun `should return groups list when exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val groups = listOf(ServiceGroup(Uuid.random(), businessId, "Group", Instant.fromEpochMilliseconds(0)))
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { serviceDataSource.getServiceGroups(businessId) } returns groups
        }

        whenn()
        val result = fixture.sut(businessId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(groups, result.getOrNull())
    }
}
