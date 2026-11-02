package com.bookk.appointments.domain.impl.operation

import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.BookingQuoteError
import com.bookk.appointments.domain.api.entity.ClientSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.InstantAppointmentDraft
import com.bookk.appointments.domain.api.entity.RequestedService
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.appointments.domain.api.operation.CreateInstantAppointment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.DeletedClientDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.business.domain.api.appointment.operation.GetStaffBookingContext
import com.bookk.business.domain.api.client.entity.ClientRemote
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.business.client.api.BusinessClient
import com.bookk.server.business.client.api.QuoteClaims
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import library.permissions.EmployeeAccessSuspended
import library.permissions.ResourcePermission
import library.signing.TokenValidator
import library.signing.TokenValidatorFactory
import org.joda.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class CreateInstantAppointmentImplTest {

    private class SutFixture(val callerId: Uuid = Uuid.random()) {
        val requestDataSource = mockk<AppointmentRequestDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val settingsDataSource = mockk<AppointmentSettingsDataSource>()
        val settings = mockk<AppointmentSettings>()
        val transactionManager = mockk<TransactionManager>()
        val tokenValidatorFactory = mockk<TokenValidatorFactory>()
        val businessClient = mockk<BusinessClient>()
        val deletedUserDataSource = mockk<DeletedUserDataSource>()
        val deletedClientDataSource = mockk<DeletedClientDataSource>()
        val businessId = Uuid.random()
        val service = Service.stub(businessId = businessId, price = Money.parse("UAH 250.00"), duration = 45.minutes)
        val clientUserId = Uuid.random()
        val context = AppointmentBookingContext.stub(
            employee = Employee.stub(businessId = businessId, userId = callerId),
            client = ClientRemote.stub(userId = clientUserId),
            services = listOf(service)
        )
        val draft = InstantAppointmentDraft.stub(
            businessId = businessId,
            employeeId = context.employee.id,
            clientId = context.client.id,
            services = listOf(RequestedService.stub(serviceId = service.id, count = 2)),
            date = Instant.parse("2099-01-01T10:00:00Z")
        )
        val stored = mutableListOf<Appointment>()
        val storedInTransaction = mutableListOf<Boolean>()
        val permissionReadInTransaction = mutableListOf<Boolean>()

        val sut = CreateInstantAppointmentImpl(
            requestDataSource,
            appointmentPermissionDataSource,
            appointmentDataSource,
            settingsDataSource,
            transactionManager,
            tokenValidatorFactory,
            businessClient,
            deletedUserDataSource,
            deletedClientDataSource
        )

        init {
            transactionManager.mockTransaction()
            coEvery { requestDataSource.reserveOfferToken(draft.offerToken) } returns true
            coEvery { requestDataSource.releaseOfferToken(draft.offerToken) } returns Unit
            coEvery {
                businessClient.getStaffBookingContext(businessId, draft.employeeId, draft.clientId, listOf(service.id))
            } returns Result.success(context)
            grant(ResourcePermission(view = true, update = false, delete = false))
            coEvery { deletedUserDataSource.isDeletedForShare(any()) } returns false
            coEvery { deletedClientDataSource.isDeletedForShare(any(), any()) } returns false
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.isInWorkday(any()) } returns true
            coEvery { settings.isInWorktime(any(), any()) } returns true
            coEvery { appointmentDataSource.hasOverlapsWith(any<Appointment>()) } returns false
            coEvery { appointmentDataSource.create(any<Appointment>()) } coAnswers {
                stored += firstArg<Appointment>()
                storedInTransaction += transactionManager.isInTransaction()
                firstArg()
            }
            acceptQuote(total = "UAH 500.00", duration = 90.minutes.toString())
        }

        fun grant(permission: ResourcePermission) {
            coEvery { appointmentPermissionDataSource.getPermission(callerId, businessId) } coAnswers {
                permissionReadInTransaction += transactionManager.isInTransaction()
                permission
            }
        }

        fun bookAnotherEmployee() {
            val anotherEmployee = context.employee.copy(userId = Uuid.random())
            coEvery { businessClient.getStaffBookingContext(any(), any(), any(), any()) } returns Result.success(context.copy(employee = anotherEmployee))
        }

        fun acceptQuote(total: String, duration: String, businessIdClaim: String = businessId.toString()) {
            val validator = mockk<TokenValidator>()
            val verifier = mockk<JWTVerifier>()
            val decoded = mockk<DecodedJWT>()
            every { tokenValidatorFactory.forType(any()) } returns validator
            every { validator.verifier } returns verifier
            every { verifier.verify(draft.offerToken) } returns decoded
            every { decoded.getClaim(QuoteClaims.CLAIM_BUSINESS_ID) } returns claim(businessIdClaim)
            every { decoded.getClaim(QuoteClaims.CLAIM_TOTAL) } returns claim(total)
            every { decoded.getClaim(QuoteClaims.CLAIM_DURATION) } returns claim(duration)
            every { decoded.getClaim(QuoteClaims.CLAIM_SERVICES) } returns mockk {
                every { asList(String::class.java) } returns QuoteClaims.encodeServiceCounts(listOf(service.id, service.id))
            }
        }

        fun rejectQuote() {
            val validator = mockk<TokenValidator>()
            every { tokenValidatorFactory.forType(any()) } returns validator
            every { validator.verifier } returns mockk {
                every { verify(draft.offerToken) } throws JWTVerificationException("invalid")
            }
        }

        private fun claim(value: String): Claim = mockk { every { asString() } returns value }
    }

    @Test
    fun `should book the business's client into the caller's own schedule from the resolved context`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        val appointment = result.getOrThrow()
        assertEquals(listOf(appointment), fixture.stored)
        assertEquals(fixture.callerId, appointment.initiatorUserId)
        assertEquals(fixture.businessId, appointment.businessId)
        assertEquals(EmployeeSnapshot(fixture.context.employee.id, fixture.callerId, "stub-name stub-lastname"), appointment.employee)
        assertEquals(
            ClientSnapshot(fixture.context.client.id, "stub-name stub-lastname", "+10000000000", "stub@client.com", fixture.clientUserId),
            appointment.client
        )
        val serviceSnapshot = ServiceSnapshot(fixture.service.id, fixture.service.name, fixture.service.group.id, fixture.service.price, fixture.service.duration)
        assertEquals(listOf(serviceSnapshot, serviceSnapshot), appointment.services)
        assertEquals(AppointmentStatus.SCHEDULED, appointment.status)
        assertEquals(fixture.draft.date, appointment.date)
        assertEquals(fixture.draft.note, appointment.note)
        assertEquals(listOf(true), fixture.storedInTransaction)
    }

    @Test
    fun `should let a caller with the update grant book another employee's schedule`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.bookAnotherEmployee()
        fixture.grant(ResourcePermission(view = true, update = true, delete = false))

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.isSuccess)
        assertEquals(fixture.callerId, fixture.stored.single().initiatorUserId)
    }

    @Test
    fun `should refuse a caller with only the view grant booking another employee's schedule`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.bookAnotherEmployee()

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        assertTrue(fixture.stored.isEmpty())
        coVerify(exactly = 1) { fixture.requestDataSource.releaseOfferToken(fixture.draft.offerToken) }
    }

    @Test
    fun `should refuse a caller with no grant in the business, even for their own schedule`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.grant(ResourcePermission.NONE)

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        assertTrue(fixture.stored.isEmpty())
    }

    @Test
    fun `should refuse a suspended employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.appointmentPermissionDataSource.getPermission(fixture.callerId, fixture.businessId) } throws EmployeeAccessSuspended()

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.exceptionOrNull() is EmployeeAccessSuspended)
        assertTrue(fixture.stored.isEmpty())
        coVerify(exactly = 1) { fixture.requestDataSource.releaseOfferToken(fixture.draft.offerToken) }
    }

    @Test
    fun `should check the permission in a transaction before taking the booking locks`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertEquals(listOf(true), fixture.permissionReadInTransaction)
        coVerifyOrder {
            fixture.appointmentPermissionDataSource.getPermission(fixture.callerId, fixture.businessId)
            fixture.deletedUserDataSource.isDeletedForShare(fixture.clientUserId)
        }
    }

    @Test
    fun `should refuse a quote token that was already used`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.requestDataSource.reserveOfferToken(fixture.draft.offerToken) } returns false

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.exceptionOrNull() is BookingQuoteError.TokenAlreadyUsed)
        coVerify(exactly = 0) { fixture.businessClient.getStaffBookingContext(any(), any(), any(), any()) }
        coVerify(exactly = 0) { fixture.requestDataSource.releaseOfferToken(any()) }
    }

    @Test
    fun `should refuse an invalid quote token before asking business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.rejectQuote()

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.exceptionOrNull() is BookingQuoteError.ServicesSignatureMiss)
        coVerify(exactly = 0) { fixture.businessClient.getStaffBookingContext(any(), any(), any(), any()) }
        coVerify(exactly = 1) { fixture.requestDataSource.releaseOfferToken(fixture.draft.offerToken) }
    }

    @Test
    fun `should refuse a quote token issued for another business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.acceptQuote(total = "UAH 500.00", duration = 90.minutes.toString(), businessIdClaim = Uuid.random().toString())

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.exceptionOrNull() is BookingQuoteError.ServicesSignatureMiss)
    }

    @Test
    fun `should report a price that changed since the quote`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.acceptQuote(total = "UAH 400.00", duration = 90.minutes.toString())

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.exceptionOrNull() is BookingQuoteError.PriceChanged)
        assertTrue(fixture.stored.isEmpty())
    }

    @Test
    fun `should report a duration that changed since the quote`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.acceptQuote(total = "UAH 500.00", duration = 60.minutes.toString())

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.exceptionOrNull() is BookingQuoteError.DurationChanged)
        assertTrue(fixture.stored.isEmpty())
    }

    @Test
    fun `should pass on a client business does not know and give the token back`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery {
            fixture.businessClient.getStaffBookingContext(any(), any(), any(), any())
        } returns Result.failure(GetStaffBookingContext.Error.ClientNotFound())

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.exceptionOrNull() is GetStaffBookingContext.Error.ClientNotFound)
        coVerify(exactly = 1) { fixture.requestDataSource.releaseOfferToken(fixture.draft.offerToken) }
    }

    @Test
    fun `should reject a booking for a client whose account was deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.deletedUserDataSource.isDeletedForShare(fixture.clientUserId) } returns true

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        assertTrue(fixture.stored.isEmpty())
    }

    @Test
    fun `should reject a booking for a client the business deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.deletedClientDataSource.isDeletedForShare(fixture.businessId, fixture.context.client.id) } returns true

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        assertTrue(fixture.stored.isEmpty())
    }

    @Test
    fun `should not look up an account for a client without one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery {
            fixture.businessClient.getStaffBookingContext(any(), any(), any(), any())
        } returns Result.success(fixture.context.copy(client = ClientRemote.stub(id = fixture.context.client.id, userId = null)))

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.isSuccess)
        assertEquals(null, fixture.stored.single().client.userId)
        coVerify(exactly = 0) { fixture.deletedUserDataSource.isDeletedForShare(any()) }
    }

    @Test
    fun `should check both tombstones before locking the business settings`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut(fixture.callerId, fixture.draft)

        then()
        coVerifyOrder {
            fixture.deletedUserDataSource.isDeletedForShare(fixture.clientUserId)
            fixture.deletedClientDataSource.isDeletedForShare(fixture.businessId, fixture.context.client.id)
            fixture.settingsDataSource.getForUpdate(fixture.businessId)
            fixture.appointmentDataSource.hasOverlapsWith(any<Appointment>())
            fixture.appointmentDataSource.create(any<Appointment>())
        }
    }

    @Test
    fun `should give the token back when the slot can't be booked`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.appointmentDataSource.hasOverlapsWith(any<Appointment>()) } returns true

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.exceptionOrNull() is CreateInstantAppointment.Error.AppointmentForThisTimeExists)
        assertTrue(fixture.stored.isEmpty())
        coVerify(exactly = 1) { fixture.requestDataSource.releaseOfferToken(fixture.draft.offerToken) }
    }

    @Test
    fun `should keep the token spent after booking`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut(fixture.callerId, fixture.draft)

        then()
        coVerify(exactly = 1) { fixture.requestDataSource.reserveOfferToken(fixture.draft.offerToken) }
        coVerify(exactly = 0) { fixture.requestDataSource.releaseOfferToken(any()) }
    }

    @Test
    fun `should refuse a date in the past`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val draft = fixture.draft.copy(date = Instant.parse("2000-01-01T10:00:00Z"))

        whenn()
        val result = fixture.sut(fixture.callerId, draft)

        then()
        assertTrue(result.exceptionOrNull() is CreateInstantAppointment.Error.DateInThePastNotAllowed)
        assertTrue(fixture.stored.isEmpty())
    }

    @Test
    fun `should refuse a day the business doesn't work`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.settings.isInWorkday(fixture.draft.date) } returns false

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.exceptionOrNull() is CreateInstantAppointment.Error.RequestForThisDateNotAllowed)
        assertTrue(fixture.stored.isEmpty())
    }

    @Test
    fun `should refuse a slot outside working hours`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.settings.isInWorktime(fixture.draft.date, any()) } returns false

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.exceptionOrNull() is CreateInstantAppointment.Error.RequestForThisTimeNotAllowed)
        assertTrue(fixture.stored.isEmpty())
    }

    @Test
    fun `should return not found when the business has no appointment settings`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.settingsDataSource.getForUpdate(fixture.businessId) } returns null

        whenn()
        val result = fixture.sut(fixture.callerId, fixture.draft)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        assertTrue(fixture.stored.isEmpty())
    }
}
