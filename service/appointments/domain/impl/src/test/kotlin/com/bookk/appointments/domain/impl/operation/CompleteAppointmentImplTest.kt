package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentCompletedBy
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.AppointmentStatusError
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.PriceAdjustment
import com.bookk.appointments.domain.api.entity.PriceAdjustmentDraft
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.appointments.domain.api.operation.CompleteAppointment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.business.domain.api.service.entity.Service
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
import library.permissions.EmployeeAccessSuspended
import library.permissions.ResourcePermission
import org.joda.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

internal class CompleteAppointmentImplTest {

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val businessClient = mockk<BusinessClient>()
        val transactionManager = mockk<TransactionManager>()

        val sut = CompleteAppointmentImpl(
            appointmentDataSource,
            appointmentPermissionDataSource,
            businessClient,
            transactionManager
        )

        fun givenLockedAppointment(userId: Uuid, appointment: Appointment, permission: ResourcePermission = updatePermission) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.getForUpdate(appointment.id) } returns appointment
            coEvery { appointmentPermissionDataSource.getPermission(userId, appointment.businessId) } returns permission
        }

        companion object {
            val updatePermission = ResourcePermission(view = false, update = true, delete = false)
        }
    }

    @Test
    fun `should complete started scheduled appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        val completed = appointment.copy(status = AppointmentStatus.COMPLETED, completedBy = AppointmentCompletedBy.USER)
        with(fixture) {
            givenLockedAppointment(userId, appointment)
            coEvery { appointmentDataSource.markCompletedByUser(appointment.id) } returns completed
        }

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, priceAdjustment = null)

        then()
        assertEquals(completed, result.getOrNull())
        coVerify(exactly = 1) { fixture.appointmentDataSource.markCompletedByUser(appointment.id) }
    }

    @Test
    fun `should return already completed appointment unchanged`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub().copy(status = AppointmentStatus.COMPLETED, completedBy = AppointmentCompletedBy.SYSTEM)
        fixture.givenLockedAppointment(userId, appointment)

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, priceAdjustment = null)

        then()
        assertEquals(appointment, result.getOrNull())
        coVerify(exactly = 0) { fixture.appointmentDataSource.markCompletedByUser(any()) }
    }

    @Test
    fun `should complete own appointment with view permission`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub().copy(employee = EmployeeSnapshot.stub(userId = userId))
        with(fixture) {
            givenLockedAppointment(userId, appointment, ResourcePermission(view = true, update = false, delete = false))
            coEvery { appointmentDataSource.markCompletedByUser(appointment.id) } returns appointment.copy(status = AppointmentStatus.COMPLETED)
        }

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, priceAdjustment = null)

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should return failure when user has view permission but appointment belongs to another employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        fixture.givenLockedAppointment(userId, appointment, ResourcePermission(view = true, update = false, delete = false))

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, priceAdjustment = null)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.appointmentDataSource.markCompletedByUser(any()) }
    }

    @Test
    fun `should return failure when caller is a suspended employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.getForUpdate(appointment.id) } returns appointment
            coEvery { appointmentPermissionDataSource.getPermission(userId, appointment.businessId) } throws EmployeeAccessSuspended()
        }

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, priceAdjustment = null)

        then()
        assertTrue(result.exceptionOrNull() is EmployeeAccessSuspended)
        coVerify(exactly = 0) { fixture.appointmentDataSource.markCompletedByUser(any()) }
    }

    @Test
    fun `should return failure when appointment is cancelled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub().copy(status = AppointmentStatus.CANCELLED)
        fixture.givenLockedAppointment(userId, appointment)

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, priceAdjustment = null)

        then()
        assertTrue(result.exceptionOrNull() is AppointmentStatusError.AlreadyCancelled)
        coVerify(exactly = 0) { fixture.appointmentDataSource.markCompletedByUser(any()) }
    }

    @Test
    fun `should return failure when appointment is marked as no-show`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub().copy(status = AppointmentStatus.NO_SHOW)
        fixture.givenLockedAppointment(userId, appointment)

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, priceAdjustment = null)

        then()
        assertTrue(result.exceptionOrNull() is AppointmentStatusError.MarkedNoShow)
        coVerify(exactly = 0) { fixture.appointmentDataSource.markCompletedByUser(any()) }
    }

    @Test
    fun `should return failure when appointment has not started yet`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub(date = Clock.System.now() + 1.hours)
        fixture.givenLockedAppointment(userId, appointment)

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, priceAdjustment = null)

        then()
        assertTrue(result.exceptionOrNull() is AppointmentStatusError.NotStarted)
        coVerify(exactly = 0) { fixture.appointmentDataSource.markCompletedByUser(any()) }
    }

    @Test
    fun `should return failure when appointment does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val appointmentId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.getForUpdate(appointmentId) } throws Error.NotFound()
        }

        whenn()
        val result = fixture.sut.invoke(Uuid.random(), appointmentId, priceAdjustment = null)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should attach price adjustment when completing appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        val completed = appointment.copy(status = AppointmentStatus.COMPLETED, completedBy = AppointmentCompletedBy.USER)
        val draft = PriceAdjustmentDraft.stub(price = Money.parse("USD 80"), reason = "Discount")
        val expectedAdjustment = PriceAdjustment(additionalServices = emptyList(), price = draft.price, reason = draft.reason)
        val adjusted = completed.copy(priceAdjustment = expectedAdjustment)
        with(fixture) {
            givenLockedAppointment(userId, appointment)
            coEvery { appointmentDataSource.markCompletedByUser(appointment.id) } returns completed
            coEvery { appointmentDataSource.adjustPrice(appointment.id, expectedAdjustment) } returns adjusted
        }

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, draft)

        then()
        assertEquals(adjusted, result.getOrNull())
        coVerify(exactly = 1) { fixture.appointmentDataSource.adjustPrice(appointment.id, expectedAdjustment) }
        coVerify(exactly = 0) { fixture.businessClient.getServicesByIds(any(), any()) }
    }

    @Test
    fun `should snapshot additional services resolved from the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        val service = Service.stub(businessId = appointment.businessId, price = Money.parse("USD 30"))
        val draft = PriceAdjustmentDraft.stub(additionalServiceIds = listOf(service.id, service.id))
        val snapshot = ServiceSnapshot(id = service.id, name = service.name, groupId = service.group.id, price = service.price, duration = service.duration)
        val expectedAdjustment = PriceAdjustment(additionalServices = listOf(snapshot, snapshot), price = draft.price, reason = draft.reason)
        val completed = appointment.copy(status = AppointmentStatus.COMPLETED, completedBy = AppointmentCompletedBy.USER)
        with(fixture) {
            givenLockedAppointment(userId, appointment)
            coEvery { businessClient.getServicesByIds(appointment.businessId, draft.additionalServiceIds) } returns Result.success(listOf(service, service))
            coEvery { appointmentDataSource.markCompletedByUser(appointment.id) } returns completed
            coEvery { appointmentDataSource.adjustPrice(appointment.id, any()) } returns completed.copy(priceAdjustment = expectedAdjustment)
        }

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, draft)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.appointmentDataSource.adjustPrice(appointment.id, expectedAdjustment) }
    }

    @Test
    fun `should store blank adjustment reason as absent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        val completed = appointment.copy(status = AppointmentStatus.COMPLETED, completedBy = AppointmentCompletedBy.USER)
        val draft = PriceAdjustmentDraft.stub(reason = "   ")
        val expectedAdjustment = PriceAdjustment(additionalServices = emptyList(), price = draft.price, reason = null)
        with(fixture) {
            givenLockedAppointment(userId, appointment)
            coEvery { appointmentDataSource.markCompletedByUser(appointment.id) } returns completed
            coEvery { appointmentDataSource.adjustPrice(appointment.id, any()) } returns completed.copy(priceAdjustment = expectedAdjustment)
        }

        whenn()
        fixture.sut.invoke(userId, appointment.id, draft)

        then()
        coVerify(exactly = 1) { fixture.appointmentDataSource.adjustPrice(appointment.id, expectedAdjustment) }
    }

    @Test
    fun `should replace price adjustment of an already completed appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub().copy(
            status = AppointmentStatus.COMPLETED,
            completedBy = AppointmentCompletedBy.SYSTEM,
            priceAdjustment = PriceAdjustment.stub()
        )
        val draft = PriceAdjustmentDraft.stub(price = Money.parse("USD 90"), reason = null)
        val expectedAdjustment = PriceAdjustment(additionalServices = emptyList(), price = draft.price, reason = null)
        with(fixture) {
            givenLockedAppointment(userId, appointment)
            coEvery { appointmentDataSource.adjustPrice(appointment.id, expectedAdjustment) } returns appointment.copy(priceAdjustment = expectedAdjustment)
        }

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, draft)

        then()
        assertEquals(expectedAdjustment, result.getOrNull()?.priceAdjustment)
        assertEquals(AppointmentCompletedBy.SYSTEM, result.getOrNull()?.completedBy)
        coVerify(exactly = 0) { fixture.appointmentDataSource.markCompletedByUser(any()) }
    }

    @Test
    fun `should not resolve or attach price adjustment when appointment cannot be completed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub().copy(status = AppointmentStatus.CANCELLED)
        fixture.givenLockedAppointment(userId, appointment)

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, PriceAdjustmentDraft.stub(additionalServiceIds = listOf(Uuid.random())))

        then()
        assertTrue(result.exceptionOrNull() is AppointmentStatusError.AlreadyCancelled)
        coVerify(exactly = 0) { fixture.businessClient.getServicesByIds(any(), any()) }
        coVerify(exactly = 0) { fixture.appointmentDataSource.adjustPrice(any(), any()) }
    }

    @Test
    fun `should return failure when adjusted price is negative`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut.invoke(Uuid.random(), Uuid.random(), PriceAdjustmentDraft.stub(price = Money.parse("USD -1")))

        then()
        assertTrue(result.exceptionOrNull() is CompleteAppointment.Error.NegativePrice)
        coVerify(exactly = 0) { fixture.appointmentDataSource.getForUpdate(any()) }
    }

    @Test
    fun `should accept a zero adjusted price`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        val completed = appointment.copy(status = AppointmentStatus.COMPLETED, completedBy = AppointmentCompletedBy.USER)
        with(fixture) {
            givenLockedAppointment(userId, appointment)
            coEvery { appointmentDataSource.markCompletedByUser(appointment.id) } returns completed
            coEvery { appointmentDataSource.adjustPrice(appointment.id, any()) } returns completed
        }

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, PriceAdjustmentDraft.stub(price = Money.parse("USD 0")))

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should return failure when adjustment reason is too long`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val reason = "a".repeat(PriceAdjustmentDraft.REASON_MAX_LENGTH + 1)

        whenn()
        val result = fixture.sut.invoke(Uuid.random(), Uuid.random(), PriceAdjustmentDraft.stub(reason = reason))

        then()
        assertTrue(result.exceptionOrNull() is CompleteAppointment.Error.ReasonTooLong)
        coVerify(exactly = 0) { fixture.appointmentDataSource.getForUpdate(any()) }
    }

    @Test
    fun `should return failure when adjusted price currency differs from the appointment currency`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        with(fixture) {
            givenLockedAppointment(userId, appointment)
            coEvery { appointmentDataSource.markCompletedByUser(appointment.id) } returns appointment.copy(status = AppointmentStatus.COMPLETED)
        }

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, PriceAdjustmentDraft.stub(price = Money.parse("EUR 80")))

        then()
        assertTrue(result.exceptionOrNull() is CompleteAppointment.Error.CurrencyMismatch)
        coVerify(exactly = 0) { fixture.appointmentDataSource.adjustPrice(any(), any()) }
    }

    @Test
    fun `should return failure when an additional service cannot be resolved`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        val draft = PriceAdjustmentDraft.stub(additionalServiceIds = listOf(Uuid.random()))
        val serviceNotFound = Error.NotFound()
        with(fixture) {
            givenLockedAppointment(userId, appointment)
            coEvery { appointmentDataSource.markCompletedByUser(appointment.id) } returns appointment.copy(status = AppointmentStatus.COMPLETED)
            coEvery { businessClient.getServicesByIds(appointment.businessId, draft.additionalServiceIds) } returns Result.failure(serviceNotFound)
        }

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, draft)

        then()
        assertEquals(serviceNotFound, result.exceptionOrNull())
        coVerify(exactly = 0) { fixture.appointmentDataSource.adjustPrice(any(), any()) }
    }

    @Test
    fun `should not resolve additional services when caller lacks permission`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        fixture.givenLockedAppointment(userId, appointment, ResourcePermission.NONE)

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, PriceAdjustmentDraft.stub(additionalServiceIds = listOf(Uuid.random())))

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.businessClient.getServicesByIds(any(), any()) }
    }

    @Test
    fun `should leave price adjustment untouched when completing without one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        val completed = appointment.copy(status = AppointmentStatus.COMPLETED, completedBy = AppointmentCompletedBy.USER)
        with(fixture) {
            givenLockedAppointment(userId, appointment)
            coEvery { appointmentDataSource.markCompletedByUser(appointment.id) } returns completed
        }

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id, priceAdjustment = null)

        then()
        assertNull(result.getOrNull()?.priceAdjustment)
        coVerify(exactly = 0) { fixture.appointmentDataSource.adjustPrice(any(), any()) }
    }
}
