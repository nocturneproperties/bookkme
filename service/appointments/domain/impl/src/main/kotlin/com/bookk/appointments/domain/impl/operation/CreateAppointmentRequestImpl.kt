package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestDraft
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.BookingQuoteError
import com.bookk.appointments.domain.api.operation.CreateAppointment
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest.Error.DateInThePastNotAllowed
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest.Error.DemoRestricted
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest.Error.PendingRequestsLimitReached
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.appointments.domain.datasource.DeletedClientDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.runCleaningUpOnFailure
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.library.serializer.moneyFormatter
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.business.client.api.BusinessClient
import library.schedule.Schedule
import library.signing.TokenValidatorFactory
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.uuid.Uuid

private val createAppointmentRequestLogger = LoggerFactory.getLogger(CreateAppointmentRequestImpl::class.java)

internal class CreateAppointmentRequestImpl(
    private val requestDataSource: AppointmentRequestDataSource,
    private val appointmentDataSource: AppointmentDataSource,
    private val settingsDataSource: AppointmentSettingsDataSource,
    private val subscriptionDataSource: AppointmentSubscriptionDataSource,
    private val eventProducer: StandardEventProducer,
    private val createAppointment: CreateAppointment,
    private val transactionManager: TransactionManager,
    private val tokenValidatorFactory: TokenValidatorFactory,
    private val businessClient: BusinessClient,
    private val deletedUserDataSource: DeletedUserDataSource,
    private val deletedClientDataSource: DeletedClientDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
) : CreateAppointmentRequest {

    private val quoteVerifier = BookingQuoteVerifier(tokenValidatorFactory)
    private val employeeBookings = EmployeeBookings(appointmentDataSource)

    override suspend fun invoke(
        userId: Uuid,
        draft: AppointmentRequestDraft,
        isDemo: Boolean,
    ): Result<Unit> = runSuspendCatching {
        if (isDemo) requireMembership(userId, draft.businessId)
        if (!requestDataSource.reserveOfferToken(draft.offerToken)) throw BookingQuoteError.TokenAlreadyUsed()
        runCleaningUpOnFailure(
            action = { book(userId, draft) },
            cleanUp = { releaseOfferTokenBestEffort(draft.offerToken) },
        )
    }

    private suspend fun requireMembership(userId: Uuid, businessId: Uuid) {
        val isMember = transactionManager.transactionOrThrow {
            appointmentPermissionDataSource.isMember(userId, businessId)
        }
        if (!isMember) throw DemoRestricted()
    }

    private suspend fun book(userId: Uuid, draft: AppointmentRequestDraft) {
        val verifiedOffer = quoteVerifier.verify(draft.businessId, draft.services, draft.offerToken)
        val bookingContext = businessClient.getAppointmentBookingContext(
            businessId = draft.businessId,
            employeeId = draft.employeeId,
            userId = userId,
            serviceIds = draft.services.map { it.serviceId },
        ).getOrThrow()
        val request = buildAppointmentRequest(userId, draft, bookingContext)

        quoteVerifier.verifyOfferUnchanged(verifiedOffer, request.totalAmount, request.dateEnd - request.date)

        createOrRequestAppointment(userId, request, bookingContext.employee.schedule).getOrThrow()
    }

    private suspend fun createOrRequestAppointment(
        userId: Uuid,
        request: AppointmentRequest,
        employeeSchedule: Schedule,
    ): Result<Unit> = transactionManager.transaction<Unit> {
        if (deletedUserDataSource.isDeletedForShare(userId)) throw Error.OperationNotAllowed()
        if (deletedClientDataSource.isDeletedForShare(request.businessId, request.client.id)) {
            throw Error.OperationNotAllowed()
        }
        val settings = settingsDataSource.getForUpdate(request.businessId) ?: throw Error.NotFound()

        if (settings.automaticApproval) {
            return@transaction createAppointment.createBypassingPermissions(request, employeeSchedule)
                .map { Unit }
                .getOrThrow()
        }

        assertRequestIsSchedulable(request, settings, employeeSchedule)
        assertPendingRequestsBelowLimit(userId, request.businessId, settings)

        requestDataSource.create(request).also {
            eventProducer.send(
                AppointmentEvent.ClientVisitBooked(request.businessId, request.client.id, request.dateEnd),
            )
            sendRequestCreatedNotification(request)
        }
    }

    private suspend fun releaseOfferTokenBestEffort(offerToken: String) {
        runSuspendCatching { requestDataSource.releaseOfferToken(offerToken) }
            .onFailure {
                createAppointmentRequestLogger.error(
                    "Failed to release offer token, it stays unusable until it expires",
                    it,
                )
            }
    }

    private suspend fun assertPendingRequestsBelowLimit(userId: Uuid, businessId: Uuid, settings: AppointmentSettings) {
        val pendingRequests = requestDataSource.countPendingByUser(userId, businessId)
        if (pendingRequests >= settings.maxPendingRequestsPerClient) throw PendingRequestsLimitReached()
    }

    private suspend fun assertRequestIsSchedulable(
        request: AppointmentRequest,
        settings: AppointmentSettings,
        employeeSchedule: Schedule,
    ) {
        if (request.date < Clock.System.now()) throw DateInThePastNotAllowed()
        settings.requireBookable(request.date, request.dateEnd, employeeSchedule)
        employeeBookings.requireFree(request, settings)
    }

    private suspend fun sendRequestCreatedNotification(request: AppointmentRequest) {
        val business = subscriptionDataSource.getBusinessSnapshot(request.businessId) ?: run {
            createAppointmentRequestLogger.error("No business with id ${request.businessId} exists")
            throw Error.NotFound()
        }
        eventProducer.send(
            AppointmentEvent.RequestCreated(
                clientUserId = request.client.userId,
                employeeUserId = request.employee.userId,
                from = request.date,
                to = request.dateEnd,
                timeZone = business.timeZone,
                price = moneyFormatter.print(request.totalAmount),
                serviceNames = request.services.map { it.name },
            ),
        )
    }

    private fun buildAppointmentRequest(
        userId: Uuid,
        draft: AppointmentRequestDraft,
        context: AppointmentBookingContext,
    ) = AppointmentRequest(
        id = Uuid.random(),
        initiatorUserId = userId,
        businessId = draft.businessId,
        employee = context.employeeSnapshot(),
        client = context.requestClientSnapshot(userId),
        services = context.serviceSnapshots(draft.services),
        status = AppointmentRequestStatus.PENDING,
        date = draft.date,
        note = draft.note,
        declineReason = "",
    )
}
