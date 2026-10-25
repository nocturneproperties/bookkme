package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.BusinessAppointmentsEnabled
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.business.client.api.BusinessClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetClientBusinessesAppointmentsStatusImplTest {

    private class SutFixture {
        val businessClient = mockk<BusinessClient>()
        val subscriptionDataSource = mockk<AppointmentSubscriptionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetClientBusinessesAppointmentsStatusImpl(businessClient, subscriptionDataSource, transactionManager)
    }

    @Test
    fun `should return appointments enabled status for every business the user is a client of`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val enabledBusinessId = Uuid.random()
        val disabledBusinessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessClient.getClientBusinessIds(userId) } returns Result.success(listOf(enabledBusinessId, disabledBusinessId))
            coEvery { subscriptionDataSource.isBusinessEnabled(enabledBusinessId) } returns true
            coEvery { subscriptionDataSource.isBusinessEnabled(disabledBusinessId) } returns false
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(
            listOf(
                BusinessAppointmentsEnabled(businessId = enabledBusinessId, enabled = true),
                BusinessAppointmentsEnabled(businessId = disabledBusinessId, enabled = false)
            ),
            result.getOrNull()
        )
    }

    @Test
    fun `should return empty list when the user has no client businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessClient.getClientBusinessIds(userId) } returns Result.success(emptyList())
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
        coVerify(exactly = 0) { fixture.subscriptionDataSource.isBusinessEnabled(any()) }
    }

    @Test
    fun `should return failure when the business client call fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        coEvery { fixture.businessClient.getClientBusinessIds(userId) } returns Result.failure(Error.NotFound())

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        coVerify(exactly = 0) { fixture.subscriptionDataSource.isBusinessEnabled(any()) }
    }
}
