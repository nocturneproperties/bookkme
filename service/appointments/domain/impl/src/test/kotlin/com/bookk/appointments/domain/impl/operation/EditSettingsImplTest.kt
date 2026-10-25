package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.AppointmentSettingsUpdate
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
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
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class EditSettingsImplTest {

    private class SutFixture {
        val settingsSource = mockk<AppointmentSettingsDataSource>()
        val permissionsSource = mockk<AppointmentPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = EditSettingsImpl(settingsSource, permissionsSource, transactionManager)
    }

    @Test
    fun `should update settings successfully when valid settings provided`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val permission = ResourcePermission(view = false, update = true, delete = false)
        val update = AppointmentSettingsUpdate.stub(businessId = businessId, inBetweenBreakInMinutes = 20)
        val settings = AppointmentSettings.stub(businessId = businessId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { permissionsSource.getPermission(userId, businessId) } returns permission
            coEvery { settingsSource.update(update) } returns settings
        }

        whenn()
        val result = fixture.sut.invoke(userId, update)

        then()
        assertTrue(result.isSuccess)
        assertEquals(settings.copy(permissions = permission), result.getOrNull())
    }

    @Test
    fun `should return operation not allowed when invalid permissions`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val update = AppointmentSettingsUpdate.stub(businessId = businessId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { permissionsSource.getPermission(userId, businessId) } returns ResourcePermission(view = true, update = false, delete = false)
        }

        whenn()
        val result = fixture.sut.invoke(userId, update)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.settingsSource.update(any()) }
    }

    @Test
    fun `should return failure on any exception from datasource`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val update = AppointmentSettingsUpdate.stub(businessId = businessId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { permissionsSource.getPermission(userId, businessId) } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { settingsSource.update(update) } answers { throw IllegalStateException() }
        }

        whenn()
        val result = fixture.sut.invoke(userId, update)

        then()
        assertTrue(result.isFailure)
    }
}
