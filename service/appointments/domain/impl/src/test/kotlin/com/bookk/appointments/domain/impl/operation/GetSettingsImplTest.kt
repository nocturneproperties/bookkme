package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentSettings
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
import io.mockk.mockk
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetSettingsImplTest {

    private class SutFixture {
        val settingsSource = mockk<AppointmentSettingsDataSource>()
        val permissionsSource = mockk<AppointmentPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetSettingsImpl(settingsSource, permissionsSource, transactionManager)
    }

    @Test
    fun `should get settings successfully when valid permissions provided`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val permission = ResourcePermission(view = true, update = false, delete = false)
        val settings = AppointmentSettings.stub(businessId = businessId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { permissionsSource.getPermission(userId, businessId) } returns permission
            coEvery { settingsSource.get(businessId) } returns settings
        }

        whenn()
        val result = fixture.sut.invoke(userId, businessId)

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
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { permissionsSource.getPermission(userId, businessId) } returns ResourcePermission.NONE
        }

        whenn()
        val result = fixture.sut.invoke(userId, businessId)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should return not found when settings do not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { permissionsSource.getPermission(userId, businessId) } returns ResourcePermission(view = true, update = false, delete = false)
            coEvery { settingsSource.get(businessId) } returns null
        }

        whenn()
        val result = fixture.sut.invoke(userId, businessId)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }
}
