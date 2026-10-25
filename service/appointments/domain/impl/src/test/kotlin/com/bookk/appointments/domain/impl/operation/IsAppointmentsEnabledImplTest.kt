package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class IsAppointmentsEnabledImplTest {

    private class SutFixture {
        val subscriptionDataSource = mockk<AppointmentSubscriptionDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = IsAppointmentsEnabledImpl(subscriptionDataSource, appointmentPermissionDataSource, transactionManager)
    }

    @Test
    fun `should return true when appointments are enabled and user has read permissions`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns ResourcePermission(view = true, update = false, delete = false)
            coEvery { subscriptionDataSource.isBusinessEnabled(businessId) } returns true
        }

        whenn()
        val result = fixture.sut.invoke(userId, businessId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
    }

    @Test
    fun `should return false when appointments are disabled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns ResourcePermission(view = true, update = false, delete = false)
            coEvery { subscriptionDataSource.isBusinessEnabled(businessId) } returns false
        }

        whenn()
        val result = fixture.sut.invoke(userId, businessId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrNull())
    }

    @Test
    fun `should return false instead of failing when user has no permissions`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns ResourcePermission.NONE
        }

        whenn()
        val result = fixture.sut.invoke(userId, businessId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrNull())
    }
}
