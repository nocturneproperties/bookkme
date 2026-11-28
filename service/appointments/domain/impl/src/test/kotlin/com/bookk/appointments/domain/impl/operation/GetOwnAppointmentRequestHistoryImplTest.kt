package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.BookingPeriod
import com.bookk.appointments.domain.api.entity.OwnAppointmentRequestFilter
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Page
import com.bookk.core.domain.entity.PageCursor
import com.bookk.core.domain.entity.PageError
import com.bookk.core.domain.entity.PageRequest
import com.bookk.core.test.TestClock
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GetOwnAppointmentRequestHistoryImplTest {

    private class SutFixture {
        val dataSource = mockk<AppointmentRequestDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val clock = TestClock()
        val sut = GetOwnAppointmentRequestHistoryImpl(dataSource, transactionManager, clock)
        val userId = Uuid.random()
    }

    @Test
    fun `should return a page of the requests the caller made with the next cursor`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = AppointmentRequest.stub(initiatorUserId = fixture.userId)
        val next = PageCursor(after = request.date, id = request.id)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { dataSource.getPageOfInitiator(userId, OwnAppointmentRequestFilter(), clock.now, any()) } returns
                Page(listOf(request), next)
        }

        whenn()
        val result = fixture.sut(fixture.userId, OwnAppointmentRequestFilter(), null, null)

        then()
        val page = result.getOrThrow()
        assertEquals(listOf(request), page.data)
        assertEquals(next.encode(), page.nextCursor)
    }

    @Test
    fun `should have no next cursor on the last page`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { dataSource.getPageOfInitiator(userId, any(), any(), any()) } returns
                Page(listOf(AppointmentRequest.stub(initiatorUserId = userId)), next = null)
        }

        whenn()
        val result = fixture.sut(fixture.userId, OwnAppointmentRequestFilter(), null, null)

        then()
        assertNull(result.getOrThrow().nextCursor)
    }

    @Test
    fun `should pass the filter, the current time and the page after the cursor to the data source`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val cursor = PageCursor(after = Instant.fromEpochSeconds(1_800_000_000), id = Uuid.random())
        val filter = OwnAppointmentRequestFilter(
            period = BookingPeriod.PAST,
            statuses = setOf(AppointmentRequestStatus.DECLINED),
        )
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { dataSource.getPageOfInitiator(userId, filter, clock.now, any()) } returns
                Page(emptyList(), next = null)
        }

        whenn()
        val result = fixture.sut(fixture.userId, filter, limit = 10, cursor = cursor.encode())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.dataSource.getPageOfInitiator(
                fixture.userId,
                filter,
                fixture.clock.now,
                match<PageRequest> { it.limit == 10 && it.after == cursor },
            )
        }
    }

    @Test
    fun `should reject an invalid limit without opening a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.userId, OwnAppointmentRequestFilter(), 0, null)

        then()
        assertTrue(result.exceptionOrNull() is PageError.LimitInvalid)
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should reject an invalid cursor without opening a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.userId, OwnAppointmentRequestFilter(), null, "garbage")

        then()
        assertTrue(result.exceptionOrNull() is PageError.CursorInvalid)
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }
}
