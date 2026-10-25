package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentCancellation
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatusError
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.TimeZone
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeclineAppointmentRequestImplTest {

    private class SutFixture {
        val requestDataSource = mockk<AppointmentRequestDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val subscriptionDataSource = mockk<AppointmentSubscriptionDataSource>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val transactionManager = mockk<TransactionManager>()

        val sut = DeclineAppointmentRequestImpl(
            requestDataSource,
            appointmentPermissionDataSource,
            subscriptionDataSource,
            eventProducer,
            transactionManager
        )
    }

    private val testUserId = Uuid.random()
    private val testBusinessId = Uuid.random()
    private val testCancellation = AppointmentCancellation(
        id = Uuid.random(),
        reason = "User declined"
    )

    @Test
    fun `should decline request successfully`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val request = AppointmentRequest.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentRequestStatus.PENDING)

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = false, update = true, delete = false)
        coEvery { fixture.requestDataSource.getForUpdate(testCancellation.id) } returns request
        coEvery { fixture.requestDataSource.decline(testCancellation.id, testCancellation.reason) } returns request.copy(status = AppointmentRequestStatus.DECLINED)
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(testBusinessId) } returns mockk(relaxed = true)

        whenn()
        val result = fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should return failure when user has read permission but request belongs to another employee`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val request = AppointmentRequest.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentRequestStatus.PENDING)

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = true, update = false, delete = false)
        coEvery { fixture.requestDataSource.getForUpdate(testCancellation.id) } returns request

        whenn()
        val result = fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is com.bookk.core.domain.entity.Error.OperationNotAllowed)
    }

    @Test
    fun `should decline own request successfully with read permission`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val request = AppointmentRequest.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentRequestStatus.PENDING, employee = EmployeeSnapshot.stub(userId = testUserId))

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = true, update = false, delete = false)
        coEvery { fixture.requestDataSource.getForUpdate(testCancellation.id) } returns request
        coEvery { fixture.requestDataSource.decline(testCancellation.id, testCancellation.reason) } returns request.copy(status = AppointmentRequestStatus.DECLINED)
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(testBusinessId) } returns mockk(relaxed = true)

        whenn()
        val result = fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should return failure when already declined`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val request = AppointmentRequest.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentRequestStatus.DECLINED)

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = false, update = true, delete = false)
        coEvery { fixture.requestDataSource.getForUpdate(testCancellation.id) } returns request

        whenn()
        val result = fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppointmentRequestStatusError.AlreadyDeclined)
    }

    @Test
    fun `should return failure when already approved`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val request = AppointmentRequest.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentRequestStatus.APPROVED)

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = false, update = true, delete = false)
        coEvery { fixture.requestDataSource.getForUpdate(testCancellation.id) } returns request

        whenn()
        val result = fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppointmentRequestStatusError.AlreadyApproved)
    }

    @Test
    fun `should send request declined event successfully`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val request = AppointmentRequest.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentRequestStatus.PENDING)

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = false, update = true, delete = false)
        coEvery { fixture.requestDataSource.getForUpdate(testCancellation.id) } returns request
        coEvery { fixture.requestDataSource.decline(testCancellation.id, testCancellation.reason) } returns request.copy(status = AppointmentRequestStatus.DECLINED)
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(testBusinessId) } returns mockk(relaxed = true)

        whenn()
        fixture.sut.invoke(testUserId, testCancellation)

        then()
        coVerify(exactly = 1) { fixture.eventProducer.send(any(AppointmentEvent.RequestRejected::class), any()) }
    }

    @Test
    fun `should propagate business time zone onto the published event`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val request = AppointmentRequest.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentRequestStatus.PENDING)
        val businessSnapshot = BusinessSnapshot.stub().copy(timeZone = TimeZone.of("Europe/Kyiv"))
        val eventSlot = slot<AppointmentEvent.RequestRejected>()

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = false, update = true, delete = false)
        coEvery { fixture.requestDataSource.getForUpdate(testCancellation.id) } returns request
        coEvery { fixture.requestDataSource.decline(testCancellation.id, testCancellation.reason) } returns request.copy(status = AppointmentRequestStatus.DECLINED)
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(testBusinessId) } returns businessSnapshot
        coEvery { fixture.eventProducer.send(capture(eventSlot), any()) } returns Unit

        whenn()
        fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertEquals(TimeZone.of("Europe/Kyiv"), eventSlot.captured.timeZone)
    }

    @Test
    fun `should check permission against the stored request business`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val requestBusinessId = Uuid.random()
        val request = AppointmentRequest.stub(id = testCancellation.id, businessId = requestBusinessId)
            .copy(status = AppointmentRequestStatus.PENDING)

        coEvery { fixture.requestDataSource.getForUpdate(testCancellation.id) } returns request
        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, requestBusinessId) } returns ResourcePermission.NONE

        whenn()
        val result = fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertTrue(result.exceptionOrNull() is com.bookk.core.domain.entity.Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.requestDataSource.decline(any(), any()) }
    }

    @Test
    fun `should read the request with a row lock`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val request = AppointmentRequest.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentRequestStatus.PENDING)

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = false, update = true, delete = false)
        coEvery { fixture.requestDataSource.getForUpdate(testCancellation.id) } returns request
        coEvery { fixture.requestDataSource.decline(testCancellation.id, testCancellation.reason) } returns request.copy(status = AppointmentRequestStatus.DECLINED)
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(testBusinessId) } returns mockk(relaxed = true)

        whenn()
        fixture.sut.invoke(testUserId, testCancellation)

        then()
        coVerify(exactly = 1) { fixture.requestDataSource.getForUpdate(testCancellation.id) }
        coVerify(exactly = 0) { fixture.requestDataSource.get(any()) }
    }

    @Test
    fun `should return failure when request does not exist`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        coEvery { fixture.requestDataSource.getForUpdate(testCancellation.id) } returns null

        whenn()
        val result = fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertTrue(result.exceptionOrNull() is com.bookk.core.domain.entity.Error.NotFound)
        coVerify(exactly = 0) { fixture.requestDataSource.decline(any(), any()) }
    }
}
