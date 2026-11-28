package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestHistoryFilter
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.HistoryDates
import com.bookk.appointments.domain.api.entity.HistoryFilterError
import com.bookk.appointments.domain.api.entity.HistoryOrder
import com.bookk.appointments.domain.api.entity.StartBounds
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.Page
import com.bookk.core.domain.entity.PageCursor
import com.bookk.core.domain.entity.PageError
import com.bookk.core.domain.entity.PageRequest
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import library.permissions.EmployeeAccessSuspended
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GetAppointmentRequestHistoryImplTest {

    private class SutFixture {
        val dataSource = mockk<AppointmentRequestDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val settingsDataSource = mockk<AppointmentSettingsDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetAppointmentRequestHistoryImpl(
            dataSource,
            appointmentPermissionDataSource,
            settingsDataSource,
            transactionManager,
        )
        val userId = Uuid.random()
        val businessId = Uuid.random()

        fun allowView() {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns
                ResourcePermission(view = true, update = false, delete = false)
        }
    }

    @Test
    fun `should return a page of the request history with the next cursor`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = AppointmentRequest.stub(businessId = fixture.businessId)
        val next = PageCursor(after = request.date, id = request.id)
        with(fixture) {
            allowView()
            coEvery { dataSource.getHistoryPage(businessId, any(), StartBounds.OPEN, any()) } returns
                Page(listOf(request), next)
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, AppointmentRequestHistoryFilter(), null, null)

        then()
        val page = result.getOrThrow()
        assertEquals(listOf(request), page.data)
        assertEquals(next.encode(), page.nextCursor)
        coVerify(exactly = 0) { fixture.settingsDataSource.get(any()) }
    }

    @Test
    fun `should pass the filter and the page after the cursor to the data source`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val cursor = PageCursor(after = Instant.fromEpochSeconds(1_800_000_000), id = Uuid.random())
        val filter = AppointmentRequestHistoryFilter(
            clientId = Uuid.random(),
            employeeId = Uuid.random(),
            statuses = setOf(AppointmentRequestStatus.PENDING),
            order = HistoryOrder.SOONEST_FIRST,
        )
        with(fixture) {
            allowView()
            coEvery { dataSource.getHistoryPage(businessId, filter, StartBounds.OPEN, any()) } returns
                Page(emptyList(), next = null)
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, filter, limit = 10, cursor = cursor.encode())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.dataSource.getHistoryPage(
                fixture.businessId,
                filter,
                StartBounds.OPEN,
                match<PageRequest> { it.limit == 10 && it.after == cursor },
            )
        }
    }

    @Test
    fun `should turn the dates into start bounds in the business time zone`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val dates = HistoryDates(from = LocalDate(2027, 3, 1), to = LocalDate(2027, 3, 1))
        val filter = AppointmentRequestHistoryFilter(dates = dates)
        val warsaw = TimeZone.of("Europe/Warsaw")
        val expected = StartBounds(
            from = Instant.parse("2027-02-28T23:00:00Z"),
            until = Instant.parse("2027-03-01T23:00:00Z"),
        )
        with(fixture) {
            allowView()
            coEvery { settingsDataSource.get(businessId) } returns AppointmentSettings(businessId, warsaw)
            coEvery { dataSource.getHistoryPage(businessId, filter, expected, any()) } returns Page(emptyList(), null)
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, filter, null, null)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 1) { fixture.dataSource.getHistoryPage(fixture.businessId, filter, expected, any()) }
    }

    @Test
    fun `should return not found for dates in a business without appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val filter = AppointmentRequestHistoryFilter(dates = HistoryDates(from = LocalDate(2027, 3, 1)))
        with(fixture) {
            allowView()
            coEvery { settingsDataSource.get(businessId) } returns null
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, filter, null, null)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        coVerify(exactly = 0) { fixture.dataSource.getHistoryPage(any(), any(), any(), any()) }
    }

    @Test
    fun `should reject dates that end before they start without opening a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val dates = HistoryDates(from = LocalDate(2027, 3, 2), to = LocalDate(2027, 3, 1))

        whenn()
        val result = fixture.sut(
            fixture.userId,
            fixture.businessId,
            AppointmentRequestHistoryFilter(dates = dates),
            null,
            null,
        )

        then()
        assertTrue(result.exceptionOrNull() is HistoryFilterError.DateRangeInvalid)
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should reject an invalid limit without opening a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, AppointmentRequestHistoryFilter(), 0, null)

        then()
        assertTrue(result.exceptionOrNull() is PageError.LimitInvalid)
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should reject an invalid cursor without opening a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, AppointmentRequestHistoryFilter(), null, "garbage")

        then()
        assertTrue(result.exceptionOrNull() is PageError.CursorInvalid)
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should return failure without reading requests when the caller has no view grant`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns
                ResourcePermission.NONE
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, AppointmentRequestHistoryFilter(), null, null)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.dataSource.getHistoryPage(any(), any(), any(), any()) }
    }

    @Test
    fun `should return failure when the caller is a suspended employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } throws
                EmployeeAccessSuspended()
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, AppointmentRequestHistoryFilter(), null, null)

        then()
        assertTrue(result.exceptionOrNull() is EmployeeAccessSuspended)
        coVerify(exactly = 0) { fixture.dataSource.getHistoryPage(any(), any(), any(), any()) }
    }
}
