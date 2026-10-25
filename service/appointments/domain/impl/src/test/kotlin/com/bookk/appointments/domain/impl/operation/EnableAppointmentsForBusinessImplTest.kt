package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.operation.EnableAppointmentsForBusiness
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.business.client.api.BusinessClient
import com.bookk.server.business.client.api.BusinessDTO
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import library.permissions.ResourcePermission
import library.schedule.DayOffRange
import library.schedule.Schedule
import library.schedule.WorkHour
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class EnableAppointmentsForBusinessImplTest {

    private class SutFixture {
        val subscriptionSource = mockk<AppointmentSubscriptionDataSource>()
        val settingsDataSource = mockk<AppointmentSettingsDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val businessClient = mockk<BusinessClient>()
        val transactionManager = mockk<TransactionManager>()

        val sut = EnableAppointmentsForBusinessImpl(
            subscriptionSource,
            settingsDataSource,
            appointmentPermissionDataSource,
            businessClient,
            transactionManager
        )
    }

    private val testUserId = Uuid.random()
    private val testBusinessId = Uuid.random()

    private fun businessDto(
        id: Uuid = testBusinessId,
        schedule: Schedule = Schedule()
    ) = BusinessDTO(
        id = id,
        name = "Business name",
        address = "Business address",
        timeZone = TimeZone.UTC,
        schedule = schedule
    )

    private fun SutFixture.acceptAttach() {
        coEvery { subscriptionSource.attachBusiness(any()) } returns Unit
        coEvery { appointmentPermissionDataSource.setPermission(testUserId, testBusinessId, ResourcePermission.FULL, false) } returns Unit
        coEvery { settingsDataSource.create(any()) } returns AppointmentSettings.stub(testBusinessId)
        coEvery { businessClient.getPermission(testUserId, testBusinessId, BusinessResource.BUSINESS) } returns
            Result.success(ResourcePermission.FULL)
    }

    @Test
    fun `should enable appointments successfully`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            acceptAttach()
            coEvery { businessClient.getBusinessById(testBusinessId) } returns Result.success(businessDto())
        }

        whenn()
        val result = fixture.sut.invoke(testUserId, testBusinessId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.appointmentPermissionDataSource.setPermission(testUserId, testBusinessId, ResourcePermission.FULL, false)
        }
    }

    @Test
    fun `should seed the business replica with the schedule owned by the business service`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val schedule = Schedule(
            workingDays = listOf(DayOfWeek.SATURDAY),
            workingHours = mapOf(
                DayOfWeek.SATURDAY to listOf(WorkHour(LocalTime(10, 0), LocalTime(14, 0)))
            ),
            dayOffs = listOf(DayOffRange(LocalDate(2099, 12, 30), LocalDate(2099, 12, 31)))
        )
        with(fixture) {
            transactionManager.mockTransaction()
            acceptAttach()
            coEvery { businessClient.getBusinessById(testBusinessId) } returns
                Result.success(businessDto(schedule = schedule))
        }

        whenn()
        val result = fixture.sut.invoke(testUserId, testBusinessId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.subscriptionSource.attachBusiness(
                match { it.id == testBusinessId && it.schedule == schedule }
            )
        }
    }

    @Test
    fun `should return failure when the business service does not know the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessClient.getPermission(testUserId, testBusinessId, BusinessResource.BUSINESS) } returns
                Result.success(ResourcePermission.FULL)
            coEvery { businessClient.getBusinessById(testBusinessId) } returns Result.failure(Error.NotFound())
        }

        whenn()
        val result = fixture.sut.invoke(testUserId, testBusinessId)

        then()
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { fixture.subscriptionSource.attachBusiness(any()) }
        coVerify(exactly = 0) { fixture.settingsDataSource.create(any()) }
    }

    @Test
    fun `should return failure when caller does not have full control of the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessClient.getPermission(testUserId, testBusinessId, BusinessResource.BUSINESS) } returns
                Result.success(ResourcePermission(view = true, update = true, delete = false))
            coEvery { businessClient.getBusinessById(testBusinessId) } returns Result.success(businessDto())
        }

        whenn()
        val result = fixture.sut.invoke(testUserId, testBusinessId)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.subscriptionSource.attachBusiness(any()) }
        coVerify(exactly = 0) { fixture.appointmentPermissionDataSource.setPermission(any(), any(), any(), any()) }
    }

    @Test
    fun `should return failure when caller has no permission record for the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessClient.getPermission(testUserId, testBusinessId, BusinessResource.BUSINESS) } returns
                Result.success(ResourcePermission.NONE)
            coEvery { businessClient.getBusinessById(testBusinessId) } returns Result.success(businessDto())
        }

        whenn()
        val result = fixture.sut.invoke(testUserId, testBusinessId)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.subscriptionSource.attachBusiness(any()) }
    }

    @Test
    fun `should return already enabled error when plugin is enabled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessClient.getPermission(testUserId, testBusinessId, BusinessResource.BUSINESS) } returns
                Result.success(ResourcePermission.FULL)
            coEvery { businessClient.getBusinessById(testBusinessId) } returns Result.success(businessDto())
            coEvery { subscriptionSource.attachBusiness(any()) } throws
                Error.UniqueConstraintFailed("business already attached", RuntimeException())
        }

        whenn()
        val result = fixture.sut.invoke(testUserId, testBusinessId)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is EnableAppointmentsForBusiness.Error.AlreadyEnabled)
    }
}
