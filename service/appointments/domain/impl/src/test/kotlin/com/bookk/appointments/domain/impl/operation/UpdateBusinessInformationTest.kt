package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.business.client.api.BusinessDTO
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import library.schedule.DayOffRange
import library.schedule.Schedule
import library.schedule.WorkHour
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class UpdateBusinessInformationTest {

    private class SutFixture {
        val subscriptionDataSource = mockk<AppointmentSubscriptionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = UpdateBusinessInformation(subscriptionDataSource, transactionManager)
    }

    private val updatedAt = Instant.fromEpochMilliseconds(1000)

    private fun makeBusinessDTO(id: Uuid = Uuid.random()): BusinessDTO = BusinessDTO(
        id = id,
        name = "Test Salon",
        address = "123 Main St",
        timeZone = TimeZone.UTC,
        schedule = Schedule(
            workingDays = listOf(DayOfWeek.SATURDAY),
            workingHours = mapOf(DayOfWeek.SATURDAY to listOf(WorkHour(LocalTime(10, 0), LocalTime(14, 0)))),
            dayOffs = listOf(DayOffRange(LocalDate(2099, 12, 30), LocalDate(2099, 12, 31)))
        )
    )

    @Test
    fun `should update business information and schedule from event`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val dto = makeBusinessDTO()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { subscriptionDataSource.updateBusiness(any(), any()) } returns Unit
        }

        whenn()
        val result = fixture.sut.invoke(dto, updatedAt)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.subscriptionDataSource.updateBusiness(
                match {
                    it.id == dto.id &&
                    it.name == dto.name &&
                    it.address == dto.address &&
                    it.timeZone == dto.timeZone &&
                    it.schedule.activeDays() == listOf(DayOfWeek.SATURDAY) &&
                    it.schedule[DayOfWeek.SATURDAY].workingTime == listOf(
                        WorkHour(LocalTime(10, 0), LocalTime(14, 0))
                    ) &&
                    it.schedule.dayOffs == listOf(DayOffRange(LocalDate(2099, 12, 30), LocalDate(2099, 12, 31)))
                },
                updatedAt
            )
        }
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val dto = makeBusinessDTO()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { subscriptionDataSource.updateBusiness(any(), any()) } throws RuntimeException("db error")
        }

        whenn()
        val result = fixture.sut.invoke(dto, updatedAt)

        then()
        assertTrue(result.isFailure)
    }
}
