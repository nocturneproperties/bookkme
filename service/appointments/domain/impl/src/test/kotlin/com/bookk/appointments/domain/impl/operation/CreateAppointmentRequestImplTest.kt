package com.bookk.appointments.domain.impl.operation

import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestDraft
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.entity.RequestedService
import com.bookk.appointments.domain.api.operation.CreateAppointment
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.business.domain.api.appointment.operation.GetAppointmentBookingContext
import com.bookk.business.domain.api.client.entity.ClientRemote
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.business.client.api.BusinessClient
import com.bookk.server.business.client.api.QuoteClaims
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.TimeZone
import library.signing.TokenValidator
import library.signing.TokenValidatorFactory
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

private fun flattenedIds(draft: AppointmentRequestDraft): List<Uuid> =
    draft.services.flatMap { requested -> List(requested.count) { requested.serviceId } }

private fun distinctIds(draft: AppointmentRequestDraft): List<Uuid> =
    draft.services.map { it.serviceId }

private fun expand(context: AppointmentBookingContext, draft: AppointmentRequestDraft): List<Service> {
    val servicesById = context.services.associateBy { it.id }
    return draft.services.flatMap { requested -> List(requested.count) { servicesById.getValue(requested.serviceId) } }
}

internal class CreateAppointmentRequestImplTest {

    private val futureDate = Instant.parse("2099-01-01T00:00:00Z")

    private class SutFixture {
        val requestDataSource = mockk<AppointmentRequestDataSource>()
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val settingsDataSource = mockk<AppointmentSettingsDataSource>()
        val createAppointment = mockk<CreateAppointment>()
        val transactionManager = mockk<TransactionManager>()
        val subscriptionDataSource = mockk<AppointmentSubscriptionDataSource>()
        val eventProducer = mockk<StandardEventProducer>()
        val tokenValidatorFactory = mockk<TokenValidatorFactory>()
        val businessClient = mockk<BusinessClient>()

        val sut = CreateAppointmentRequestImpl(
            requestDataSource,
            appointmentDataSource,
            settingsDataSource,
            subscriptionDataSource,
            eventProducer,
            createAppointment,
            transactionManager,
            tokenValidatorFactory,
            businessClient
        )

        fun mockValidToken(draft: AppointmentRequestDraft, totalAmount: String, totalDuration: String) {
            val jwtVerifier = mockk<JWTVerifier>()
            val decodedJwt = mockk<DecodedJWT>()
            val servicesClaim = mockk<Claim>()
            val totalClaim = mockk<Claim>()
            val durationClaim = mockk<Claim>()
            val businessIdClaim = mockk<Claim>()
            val validator = mockk<TokenValidator>()

            coEvery { requestDataSource.reserveOfferToken("token") } returns true
            coEvery { requestDataSource.releaseOfferToken("token") } returns Unit
            every { validator.verifier } returns jwtVerifier
            every { tokenValidatorFactory.forType(any()) } returns validator
            every { jwtVerifier.verify("token") } returns decodedJwt
            every { decodedJwt.getClaim(QuoteClaims.CLAIM_SERVICES) } returns servicesClaim
            every { decodedJwt.getClaim(QuoteClaims.CLAIM_TOTAL) } returns totalClaim
            every { decodedJwt.getClaim(QuoteClaims.CLAIM_DURATION) } returns durationClaim
            every { decodedJwt.getClaim(QuoteClaims.CLAIM_BUSINESS_ID) } returns businessIdClaim
            every { servicesClaim.asList(String::class.java) } returns QuoteClaims.encodeServiceCounts(flattenedIds(draft))
            every { totalClaim.asString() } returns totalAmount
            every { durationClaim.asString() } returns totalDuration
            every { businessIdClaim.asString() } returns draft.businessId.toString()
        }
    }

    private fun bookingContext(businessId: Uuid, employeeId: Uuid, clientId: Uuid, serviceId: Uuid): AppointmentBookingContext {
        val employee = Employee.stub(id = employeeId, businessId = businessId)
        val client = ClientRemote.stub(id = clientId)
        val service = Service.stub(id = serviceId, businessId = businessId)
        return AppointmentBookingContext(employee = employee, client = client, services = listOf(service))
    }

    private fun bookingContext(businessId: Uuid, employeeId: Uuid, clientId: Uuid, services: List<Service>): AppointmentBookingContext {
        val employee = Employee.stub(id = employeeId, businessId = businessId)
        val client = ClientRemote.stub(id = clientId)
        return AppointmentBookingContext(employee = employee, client = client, services = services)
    }

    private fun draftFor(
        context: AppointmentBookingContext,
        businessId: Uuid,
        date: Instant,
        counts: Map<Uuid, Int> = context.services.associate { it.id to 1 }
    ) = AppointmentRequestDraft(
        businessId = businessId,
        employeeId = context.employee.id,
        services = context.services.map { RequestedService(it.id, counts.getValue(it.id)) },
        date = date,
        note = "Note",
        offerToken = "token"
    )

    private fun totalOf(services: List<Service>) =
        services.map { it.price }.reduce { acc, price -> acc + price }.toString()

    private fun durationOf(services: List<Service>) =
        services.fold(Duration.ZERO) { acc, service -> acc + service.duration }.toString()

    @Test
    fun `should create request successfully when valid request provided`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)
        val settings = mockk<AppointmentSettings>()
        val businessSnapshot = BusinessSnapshot.stub()
        val createdSlot = slot<AppointmentRequest>()

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.success(context)
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            coEvery { settings.isInWorkday(draft.date) } returns true
            coEvery { settings.isInWorktime(any(), any()) } returns true
            coEvery { requestDataSource.hasOverlapsWith(any()) } returns false
            coEvery { appointmentDataSource.hasOverlapsWith(any()) } returns false
            coEvery { requestDataSource.create(capture(createdSlot)) } answers { createdSlot.captured }
            coEvery { subscriptionDataSource.getBusinessSnapshot(any()) } returns businessSnapshot
            coEvery { eventProducer.send(any(), any()) } returns Unit
            mockValidToken(draft, totalOf(expand(context, draft)), durationOf(expand(context, draft)))
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.requestDataSource.create(any()) }
        val created = createdSlot.captured
        assertEquals(userId, created.userId)
        assertEquals(businessId, created.businessId)
        assertEquals(context.employee.id, created.employee.id)
        assertEquals(context.employee.userId, created.employee.userId)
        assertEquals("${context.employee.name} ${context.employee.lastName}", created.employee.fullName)
        assertEquals(context.client.id, created.client.id)
        assertEquals("${context.client.name} ${context.client.lastName}", created.client.fullName)
        assertEquals(context.client.phone, created.client.phone)
        assertEquals(context.client.email, created.client.email)
        assertEquals(context.services.map { it.id }, created.services.map { it.id })
        assertEquals(draft.date, created.date)
        assertEquals(draft.note, created.note)
    }

    @Test
    fun `should create request successfully with multiple counts of the same service`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val serviceX = Service.stub(businessId = businessId)
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), listOf(serviceX))
        val draft = draftFor(context, businessId, futureDate, counts = mapOf(serviceX.id to 5))
        val settings = mockk<AppointmentSettings>()
        val businessSnapshot = BusinessSnapshot.stub()
        val createdSlot = slot<AppointmentRequest>()

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.success(context)
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            coEvery { settings.isInWorkday(draft.date) } returns true
            coEvery { settings.isInWorktime(any(), any()) } returns true
            coEvery { requestDataSource.hasOverlapsWith(any()) } returns false
            coEvery { appointmentDataSource.hasOverlapsWith(any()) } returns false
            coEvery { requestDataSource.create(capture(createdSlot)) } answers { createdSlot.captured }
            coEvery { subscriptionDataSource.getBusinessSnapshot(any()) } returns businessSnapshot
            coEvery { eventProducer.send(any(), any()) } returns Unit
            mockValidToken(draft, totalOf(expand(context, draft)), durationOf(expand(context, draft)))
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isSuccess)
        val created = createdSlot.captured
        assertEquals(5, created.services.count { it.id == serviceX.id })
        assertEquals(serviceX.price.multipliedBy(5), created.totalAmount)
    }

    @Test
    fun `should return failure when a requested service has a non-positive count`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val draft = AppointmentRequestDraft.stub(
            businessId = businessId,
            services = listOf(RequestedService(Uuid.random(), 0)),
            date = futureDate
        )
        coEvery { fixture.requestDataSource.reserveOfferToken("token") } returns true
        coEvery { fixture.requestDataSource.releaseOfferToken("token") } returns Unit

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointmentRequest.Error.ServicesSignatureMiss)
        coVerify(exactly = 0) { fixture.businessClient.getAppointmentBookingContext(any(), any(), any(), any()) }
    }

    @Test
    fun `should return failure when draft has duplicate entries for the same service`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val serviceId = Uuid.random()
        val draft = AppointmentRequestDraft.stub(
            businessId = businessId,
            services = listOf(RequestedService(serviceId, 2), RequestedService(serviceId, 3)),
            date = futureDate
        )
        coEvery { fixture.requestDataSource.reserveOfferToken("token") } returns true
        coEvery { fixture.requestDataSource.releaseOfferToken("token") } returns Unit

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointmentRequest.Error.ServicesSignatureMiss)
        coVerify(exactly = 0) { fixture.businessClient.getAppointmentBookingContext(any(), any(), any(), any()) }
    }

    @Test
    fun `should create request successfully when valid request provided with automatic approval`() = runUnitTest {
        given()

        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)
        val settings = mockk<AppointmentSettings>()

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.success(context)
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns true
            coEvery { createAppointment.invoke(userId, any<AppointmentRequest>()) } returns Result.success(mockk())
            mockValidToken(draft, totalOf(expand(context, draft)), durationOf(expand(context, draft)))
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.requestDataSource.create(any()) }
        coVerify(exactly = 1) { fixture.createAppointment.invoke(userId, any<AppointmentRequest>()) }
    }

    @Test
    fun `should return failure when date is not in workday`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)
        val settings = mockk<AppointmentSettings>()

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.success(context)
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            coEvery { settings.isInWorkday(draft.date) } returns false
            mockValidToken(draft, totalOf(expand(context, draft)), durationOf(expand(context, draft)))
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointmentRequest.Error.RequestForThisDateNotAllowed)
    }

    @Test
    fun `should return failure when request date is in the past`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val pastDate = Instant.parse("2000-01-01T00:00:00Z")
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, pastDate)
        val settings = mockk<AppointmentSettings>()

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.success(context)
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            mockValidToken(draft, totalOf(expand(context, draft)), durationOf(expand(context, draft)))
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointmentRequest.Error.DateInThePastNotAllowed)
    }

    @Test
    fun `should return failure when create request fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)
        val settings = mockk<AppointmentSettings>()

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.success(context)
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns true
            coEvery { createAppointment.invoke(userId, any<AppointmentRequest>()) } returns Result.failure(RuntimeException())
            mockValidToken(draft, totalOf(expand(context, draft)), durationOf(expand(context, draft)))
            transactionManager.mockTransaction()
        }
        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `should return failure when time is not in worktime`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)
        val settings = mockk<AppointmentSettings>()

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.success(context)
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            coEvery { settings.isInWorkday(draft.date) } returns true
            coEvery { settings.isInWorktime(any(), any()) } returns false
            mockValidToken(draft, totalOf(expand(context, draft)), durationOf(expand(context, draft)))
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointmentRequest.Error.RequestForThisTimeNotAllowed)
    }

    @Test
    fun `should return failure when create event fails to be sent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)
        val settings = mockk<AppointmentSettings>()
        val businessSnapshot = BusinessSnapshot.stub()

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.success(context)
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            coEvery { settings.isInWorkday(draft.date) } returns true
            coEvery { settings.isInWorktime(any(), any()) } returns true
            coEvery { requestDataSource.hasOverlapsWith(any()) } returns false
            coEvery { appointmentDataSource.hasOverlapsWith(any()) } returns false
            coEvery { requestDataSource.create(any()) } answers { firstArg() }
            coEvery { subscriptionDataSource.getBusinessSnapshot(businessId) } returns businessSnapshot
            coEvery { eventProducer.send(any(), any()) } answers { throw RuntimeException("Producer fail") }
            mockValidToken(draft, totalOf(expand(context, draft)), durationOf(expand(context, draft)))
            transactionManager.mockTransaction()
        }
        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Producer fail", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should return failure when request overlaps with existing appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)
        val settings = mockk<AppointmentSettings>()

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.success(context)
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            coEvery { settings.isInWorkday(draft.date) } returns true
            coEvery { settings.isInWorktime(any(), any()) } returns true
            coEvery { requestDataSource.hasOverlapsWith(any()) } returns true
            mockValidToken(draft, totalOf(expand(context, draft)), durationOf(expand(context, draft)))
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointmentRequest.Error.RequestForThisTimeExists)
    }

    @Test
    fun `should send event if appointment created`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)
        val settings = mockk<AppointmentSettings>()
        val businessSnapshot = BusinessSnapshot.stub()

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.success(context)
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            coEvery { settings.isInWorkday(draft.date) } returns true
            coEvery { settings.isInWorktime(any(), any()) } returns true
            coEvery { requestDataSource.hasOverlapsWith(any()) } returns false
            coEvery { appointmentDataSource.hasOverlapsWith(any()) } returns false
            coEvery { requestDataSource.create(any()) } answers { firstArg() }
            coEvery { subscriptionDataSource.getBusinessSnapshot(businessId) } returns businessSnapshot
            coEvery { eventProducer.send(any(), any()) } returns Unit
            mockValidToken(draft, totalOf(expand(context, draft)), durationOf(expand(context, draft)))
            transactionManager.mockTransaction()
        }
        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.eventProducer.send(any(AppointmentEvent.RequestCreated::class), any()) }
    }

    @Test
    fun `should return failure when price changed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.success(context)
            mockValidToken(draft, "USD 0.00", durationOf(expand(context, draft)))
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointmentRequest.Error.PriceChanged)
    }

    @Test
    fun `should return failure when duration changed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.success(context)
            mockValidToken(draft, totalOf(expand(context, draft)), "999m")
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointmentRequest.Error.DurationChanged)
    }

    @Test
    fun `should return failure when services signature does not match`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)

        with(fixture) {
            coEvery { requestDataSource.reserveOfferToken("token") } returns true
            coEvery { requestDataSource.releaseOfferToken("token") } returns Unit
            val jwtVerifier = mockk<JWTVerifier>()
            val decodedJwt = mockk<DecodedJWT>()
            val servicesClaim = mockk<Claim>()
            val totalClaim = mockk<Claim>()
            val durationClaim = mockk<Claim>()
            val businessIdClaim = mockk<Claim>()
            val validator = mockk<TokenValidator>()

            every { validator.verifier } returns jwtVerifier
            every { tokenValidatorFactory.forType(any()) } returns validator
            every { jwtVerifier.verify("token") } returns decodedJwt
            every { decodedJwt.getClaim(QuoteClaims.CLAIM_SERVICES) } returns servicesClaim
            every { decodedJwt.getClaim(QuoteClaims.CLAIM_TOTAL) } returns totalClaim
            every { decodedJwt.getClaim(QuoteClaims.CLAIM_DURATION) } returns durationClaim
            every { decodedJwt.getClaim(QuoteClaims.CLAIM_BUSINESS_ID) } returns businessIdClaim
            every { servicesClaim.asList(String::class.java) } returns listOf(Uuid.random().toString())
            every { totalClaim.asString() } returns totalOf(expand(context, draft))
            every { durationClaim.asString() } returns durationOf(expand(context, draft))
            every { businessIdClaim.asString() } returns businessId.toString()
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointmentRequest.Error.ServicesSignatureMiss)
        coVerify(exactly = 0) { fixture.businessClient.getAppointmentBookingContext(any(), any(), any(), any()) }
        coVerify(exactly = 1) { fixture.requestDataSource.releaseOfferToken("token") }
    }

    @Test
    fun `should return failure when business id in token does not match request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)

        with(fixture) {
            coEvery { requestDataSource.reserveOfferToken("token") } returns true
            coEvery { requestDataSource.releaseOfferToken("token") } returns Unit
            val jwtVerifier = mockk<JWTVerifier>()
            val decodedJwt = mockk<DecodedJWT>()
            val servicesClaim = mockk<Claim>()
            val totalClaim = mockk<Claim>()
            val durationClaim = mockk<Claim>()
            val businessIdClaim = mockk<Claim>()
            val validator = mockk<TokenValidator>()

            every { validator.verifier } returns jwtVerifier
            every { tokenValidatorFactory.forType(any()) } returns validator
            every { jwtVerifier.verify("token") } returns decodedJwt
            every { decodedJwt.getClaim(QuoteClaims.CLAIM_SERVICES) } returns servicesClaim
            every { decodedJwt.getClaim(QuoteClaims.CLAIM_TOTAL) } returns totalClaim
            every { decodedJwt.getClaim(QuoteClaims.CLAIM_DURATION) } returns durationClaim
            every { decodedJwt.getClaim(QuoteClaims.CLAIM_BUSINESS_ID) } returns businessIdClaim
            every { servicesClaim.asList(String::class.java) } returns flattenedIds(draft).map { it.toString() }
            every { totalClaim.asString() } returns totalOf(expand(context, draft))
            every { durationClaim.asString() } returns durationOf(expand(context, draft))
            every { businessIdClaim.asString() } returns Uuid.random().toString()
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointmentRequest.Error.ServicesSignatureMiss)
        coVerify(exactly = 0) { fixture.businessClient.getAppointmentBookingContext(any(), any(), any(), any()) }
    }

    @Test
    fun `should return TokenAlreadyUsed without releasing the reservation when offer token is already reserved`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val draft = AppointmentRequestDraft.stub(date = futureDate)

        with(fixture) {
            coEvery { requestDataSource.reserveOfferToken("token") } returns false
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointmentRequest.Error.TokenAlreadyUsed)
        coVerify(exactly = 0) { fixture.requestDataSource.releaseOfferToken(any()) }
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should keep the offer token reserved after the request is created`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)
        val settings = mockk<AppointmentSettings>()
        val businessSnapshot = BusinessSnapshot.stub()

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.success(context)
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            coEvery { settings.isInWorkday(draft.date) } returns true
            coEvery { settings.isInWorktime(any(), any()) } returns true
            coEvery { requestDataSource.hasOverlapsWith(any()) } returns false
            coEvery { appointmentDataSource.hasOverlapsWith(any()) } returns false
            coEvery { requestDataSource.create(any()) } answers { firstArg() }
            coEvery { subscriptionDataSource.getBusinessSnapshot(any()) } returns businessSnapshot
            coEvery { eventProducer.send(any(), any()) } returns Unit
            mockValidToken(draft, totalOf(expand(context, draft)), durationOf(expand(context, draft)))
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.requestDataSource.reserveOfferToken("token") }
        coVerify(exactly = 0) { fixture.requestDataSource.releaseOfferToken(any()) }
    }

    @Test
    fun `should propagate business time zone onto the published event`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)
        val settings = mockk<AppointmentSettings>()
        val businessSnapshot = BusinessSnapshot.stub().copy(timeZone = TimeZone.of("Europe/Kyiv"))
        val eventSlot = slot<AppointmentEvent.RequestCreated>()

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.success(context)
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            coEvery { settings.isInWorkday(draft.date) } returns true
            coEvery { settings.isInWorktime(any(), any()) } returns true
            coEvery { requestDataSource.hasOverlapsWith(any()) } returns false
            coEvery { appointmentDataSource.hasOverlapsWith(any()) } returns false
            coEvery { requestDataSource.create(any()) } answers { firstArg() }
            coEvery { subscriptionDataSource.getBusinessSnapshot(any()) } returns businessSnapshot
            coEvery { eventProducer.send(capture(eventSlot), any()) } returns Unit
            mockValidToken(draft, totalOf(expand(context, draft)), durationOf(expand(context, draft)))
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isSuccess)
        assertEquals(TimeZone.of("Europe/Kyiv"), eventSlot.captured.timeZone)
    }

    @Test
    fun `should return failure when employee is not found in business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.failure(GetAppointmentBookingContext.Error.EmployeeNotFound())
            mockValidToken(draft, totalOf(expand(context, draft)), durationOf(expand(context, draft)))
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GetAppointmentBookingContext.Error.EmployeeNotFound)
        coVerify(exactly = 0) { fixture.requestDataSource.create(any()) }
    }

    @Test
    fun `should return failure when a service is not found in business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)

        with(fixture) {
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.failure(GetAppointmentBookingContext.Error.ServiceNotFound())
            mockValidToken(draft, totalOf(expand(context, draft)), durationOf(expand(context, draft)))
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GetAppointmentBookingContext.Error.ServiceNotFound)
        coVerify(exactly = 0) { fixture.requestDataSource.create(any()) }
    }

    @Test
    fun `should return failure when reserving the offer token fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)
        val cacheFailure = Error.UnknownError(IllegalStateException("cache unavailable"))
        coEvery { fixture.requestDataSource.reserveOfferToken("token") } throws cacheFailure

        whenn()
        val result = fixture.sut.invoke(Uuid.random(), draft)

        then()
        assertSame(cacheFailure, result.exceptionOrNull())
        coVerify(exactly = 0) { fixture.requestDataSource.releaseOfferToken(any()) }
    }

    @Test
    fun `should return failure when booking context misses a requested service`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)
        with(fixture) {
            mockValidToken(draft, totalOf(context.services), durationOf(context.services))
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.success(context.copy(services = emptyList()))
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { fixture.requestDataSource.create(any()) }
        coVerify(exactly = 1) { fixture.requestDataSource.releaseOfferToken("token") }
    }

    @Test
    fun `should return the original failure when releasing the offer token fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)
        val bookingFailure = GetAppointmentBookingContext.Error.EmployeeSuspended()
        with(fixture) {
            mockValidToken(draft, totalOf(context.services), durationOf(context.services))
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } returns Result.failure(bookingFailure)
            coEvery { requestDataSource.releaseOfferToken("token") } throws Error.UnknownError(IllegalStateException("cache unavailable"))
        }

        whenn()
        val result = fixture.sut.invoke(userId, draft)

        then()
        assertSame(bookingFailure, result.exceptionOrNull())
        coVerify(exactly = 1) { fixture.requestDataSource.releaseOfferToken("token") }
    }

    @Test
    fun `should release the offer token when the call is cancelled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val context = bookingContext(businessId, Uuid.random(), Uuid.random(), Uuid.random())
        val draft = draftFor(context, businessId, futureDate)
        with(fixture) {
            mockValidToken(draft, totalOf(context.services), durationOf(context.services))
            coEvery {
                businessClient.getAppointmentBookingContext(businessId, draft.employeeId, userId, distinctIds(draft))
            } coAnswers { awaitCancellation() }
        }
        val call = launch { fixture.sut.invoke(userId, draft) }
        runCurrent()

        whenn()
        call.cancelAndJoin()

        then()
        coVerify(exactly = 1) { fixture.requestDataSource.releaseOfferToken("token") }
    }
}
