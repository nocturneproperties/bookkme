package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentCompletedBy
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.ClientSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.PriceAdjustment
import com.bookk.appointments.domain.api.entity.RequestClientSnapshot
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.appointments.domain.datasource.UserDataExportDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.ClientDetailsExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.PriceAdjustmentExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.Role
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.ServiceExport
import io.mockk.coEvery
import io.mockk.mockk
import org.joda.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ExportUserAppointmentsImplTest {

    private class SutFixture {
        val dataSource = mockk<UserDataExportDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = ExportUserAppointmentsImpl(dataSource, transactionManager)
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val someoneElse = Uuid.random()
        val readInTransaction = mutableListOf<Boolean>()
        val service = ServiceSnapshot(Uuid.random(), "Haircut", Uuid.random(), Money.parse("UAH 450.00"), 45.minutes)

        init {
            transactionManager.mockTransaction()
            coEvery { dataSource.getAppointmentsOfUser(userId) } coAnswers {
                readInTransaction += transactionManager.isInTransaction()
                emptyList()
            }
            coEvery { dataSource.getRequestsOfUser(userId) } returns emptyList()
            coEvery { dataSource.getBusinessNames(any()) } returns mapOf(businessId to "Studio")
        }

        fun stored(vararg appointments: Appointment) {
            coEvery { dataSource.getAppointmentsOfUser(userId) } returns appointments.toList()
        }

        fun storedRequests(vararg requests: AppointmentRequest) {
            coEvery { dataSource.getRequestsOfUser(userId) } returns requests.toList()
        }

        fun appointment(
            client: ClientSnapshot,
            employee: EmployeeSnapshot = EmployeeSnapshot.stub(
                userId = someoneElse,
            ),
            initiator: Uuid = someoneElse,
        ) = Appointment.stub(
            initiatorUserId = initiator,
            businessId = businessId,
            date = Instant.fromEpochMilliseconds(3_600_000),
        ).copy(
            client = client,
            employee = employee,
            services = listOf(service),
            status = AppointmentStatus.CANCELLED,
            note = "allergic to latex",
            cancellationReason = "client is ill",
            completedBy = AppointmentCompletedBy.USER,
            priceAdjustment = PriceAdjustment(
                additionalServices = listOf(service),
                price = Money.parse("UAH 500.00"),
                reason = "extra styling",
            ),
        )

        fun request(
            client: RequestClientSnapshot,
            employee: EmployeeSnapshot = EmployeeSnapshot.stub(
                userId = someoneElse,
            ),
            initiator: Uuid = someoneElse,
        ) = AppointmentRequest.stub(
            initiatorUserId = initiator,
            businessId = businessId,
            employee = employee,
        ).copy(
            client = client,
            services = listOf(service),
            status = AppointmentRequestStatus.DECLINED,
            note = "first visit",
            declineReason = "fully booked",
        )

        fun clientUser() = ClientSnapshot.stub(
            userId = userId,
        ).copy(fullName = "Olena Koval", phone = "+380501112233", email = "olena@example.com")

        fun otherClient() = ClientSnapshot.stub(
            userId = someoneElse,
        ).copy(fullName = "Ivan Petrenko", phone = "+380509998877", email = "ivan@example.com")

        fun requestingClientUser() = RequestClientSnapshot.stub(
            userId = userId,
        ).copy(fullName = "Olena Koval", phone = "+380501112233", email = "olena@example.com")

        fun otherRequestingClient() = RequestClientSnapshot.stub(
            userId = someoneElse,
        ).copy(fullName = "Ivan Petrenko", phone = "+380509998877", email = "ivan@example.com")
    }

    @Test
    fun `should export the user's own appointment as a client in full`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val appointment = fixture.appointment(client = fixture.clientUser())
        fixture.stored(appointment)

        whenn()
        val export = fixture.sut(fixture.userId).getOrThrow()

        then()
        assertEquals(
            AppointmentsUserDataExport.AppointmentExport(
                id = appointment.id,
                businessId = fixture.businessId,
                businessName = "Studio",
                roles = listOf(Role.CLIENT),
                start = Instant.fromEpochMilliseconds(3_600_000),
                end = appointment.dateEnd,
                status = "CANCELLED",
                services = listOf(ServiceExport("Haircut", "UAH 450.00", durationMinutes = 45)),
                employeeName = appointment.employee.fullName,
                client = ClientDetailsExport(
                    "Olena Koval",
                    "+380501112233",
                    "olena@example.com",
                    note = "allergic to latex",
                ),
                cancellationReason = "client is ill",
                priceAdjustment = PriceAdjustmentExport(
                    "UAH 500.00",
                    listOf(ServiceExport("Haircut", "UAH 450.00", durationMinutes = 45)),
                    reason = "extra styling",
                ),
                completedBy = "USER",
            ),
            export.appointments.single(),
        )
    }

    @Test
    fun `should leave out the client's details, notes and reasons where the user is only the employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stored(
            fixture.appointment(
                client = fixture.otherClient(),
                employee = EmployeeSnapshot.stub(userId = fixture.userId),
            ),
        )

        whenn()
        val exported = fixture.sut(fixture.userId).getOrThrow().appointments.single()

        then()
        assertEquals(listOf(Role.EMPLOYEE), exported.roles)
        assertNull(exported.client)
        assertNull(exported.cancellationReason)
        assertEquals(
            PriceAdjustmentExport("UAH 500.00", listOf(ServiceExport("Haircut", "UAH 450.00", 45)), reason = null),
            exported.priceAdjustment,
        )
        assertEquals(listOf(ServiceExport("Haircut", "UAH 450.00", 45)), exported.services)
        assertEquals("CANCELLED", exported.status)
    }

    @Test
    fun `should leave out the client's details where the user only booked for someone else`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stored(fixture.appointment(client = fixture.otherClient(), initiator = fixture.userId))

        whenn()
        val exported = fixture.sut(fixture.userId).getOrThrow().appointments.single()

        then()
        assertEquals(listOf(Role.BOOKER), exported.roles)
        assertNull(exported.client)
        assertNull(exported.cancellationReason)
    }

    @Test
    fun `should name every role the user has in an appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stored(
            fixture.appointment(
                client = fixture.clientUser(),
                employee = EmployeeSnapshot.stub(userId = fixture.userId),
                initiator = fixture.userId,
            ),
        )

        whenn()
        val exported = fixture.sut(fixture.userId).getOrThrow().appointments.single()

        then()
        assertEquals(listOf(Role.CLIENT, Role.EMPLOYEE, Role.BOOKER), exported.roles)
        assertEquals("Olena Koval", exported.client?.name)
    }

    @Test
    fun `should export empty notes and reasons as absent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stored(
            fixture.appointment(
                client = fixture.clientUser(),
            ).copy(note = "", cancellationReason = "", priceAdjustment = null, completedBy = null),
        )

        whenn()
        val exported = fixture.sut(fixture.userId).getOrThrow().appointments.single()

        then()
        assertNull(exported.client?.note)
        assertNull(exported.cancellationReason)
        assertNull(exported.priceAdjustment)
        assertNull(exported.completedBy)
    }

    @Test
    fun `should export the user's own request as a client in full`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.request(client = fixture.requestingClientUser())
        fixture.storedRequests(request)

        whenn()
        val exported = fixture.sut(fixture.userId).getOrThrow().requests.single()

        then()
        assertEquals(
            AppointmentsUserDataExport.AppointmentRequestExport(
                id = request.id,
                businessId = fixture.businessId,
                businessName = "Studio",
                roles = listOf(Role.CLIENT),
                start = request.date,
                end = request.dateEnd,
                status = "DECLINED",
                services = listOf(ServiceExport("Haircut", "UAH 450.00", 45)),
                employeeName = request.employee.fullName,
                client = ClientDetailsExport("Olena Koval", "+380501112233", "olena@example.com", note = "first visit"),
                declineReason = "fully booked",
            ),
            exported,
        )
    }

    @Test
    fun `should omit client details and decline reason of a request the user only handles as employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.storedRequests(
            fixture.request(
                client = fixture.otherRequestingClient(),
                employee = EmployeeSnapshot.stub(userId = fixture.userId),
            ),
        )

        whenn()
        val exported = fixture.sut(fixture.userId).getOrThrow().requests.single()

        then()
        assertEquals(listOf(Role.EMPLOYEE), exported.roles)
        assertNull(exported.client)
        assertNull(exported.declineReason)
    }

    @Test
    fun `should read everything inside a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut(fixture.userId)

        then()
        assertEquals(listOf(true), fixture.readInTransaction)
    }

    @Test
    fun `should fail when the appointments can't be read`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.dataSource.getAppointmentsOfUser(fixture.userId) } throws
            IllegalStateException("database unreachable")

        whenn()
        val result = fixture.sut(fixture.userId)

        then()
        assertTrue(result.isFailure)
    }
}
