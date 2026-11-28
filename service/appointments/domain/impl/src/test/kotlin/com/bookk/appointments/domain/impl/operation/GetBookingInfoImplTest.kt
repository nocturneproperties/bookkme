package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.BookingInfo
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetBookingInfoImplTest {

    private class SutFixture {
        val settingsDataSource = mockk<AppointmentSettingsDataSource>()
        val subscriptionDataSource = mockk<AppointmentSubscriptionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetBookingInfoImpl(settingsDataSource, subscriptionDataSource, transactionManager)
    }

    @Test
    fun `should return the business's appointment note`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val settings = AppointmentSettings.stub(businessId = businessId).copy(appointmentNote = "Arrive early")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { subscriptionDataSource.isBusinessEnabled(businessId) } returns true
            coEvery { settingsDataSource.get(businessId) } returns settings
        }

        whenn()
        val result = fixture.sut(businessId)

        then()
        assertEquals(BookingInfo(businessId = businessId, appointmentNote = "Arrive early"), result.getOrThrow())
    }

    @Test
    fun `should return not found when the business does not take appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { subscriptionDataSource.isBusinessEnabled(businessId) } returns false
        }

        whenn()
        val result = fixture.sut(businessId)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        coVerify(exactly = 0) { fixture.settingsDataSource.get(any()) }
    }

    @Test
    fun `should return not found when the business has no settings`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { subscriptionDataSource.isBusinessEnabled(businessId) } returns true
            coEvery { settingsDataSource.get(businessId) } returns null
        }

        whenn()
        val result = fixture.sut(businessId)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }
}
