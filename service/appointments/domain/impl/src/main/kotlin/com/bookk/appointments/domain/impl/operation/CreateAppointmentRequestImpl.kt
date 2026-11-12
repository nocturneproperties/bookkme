package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestDraft
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.BookingQuoteError
import com.bookk.appointments.domain.api.operation.CreateAppointment
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest.Error.DateInThePastNotAllowed
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest.Error.RequestForThisDateNotAllowed
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest.Error.RequestForThisTimeExists
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest.Error.RequestForThisTimeNotAllowed
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.appointments.domain.datasource.DeletedClientDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.runCleaningUpOnFailure
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.library.serializer.moneyFormatter
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.business.client.api.BusinessClient
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
) : CreateAppointmentRequest {

    private val quoteVerifier = BookingQuoteVerifier(tokenValidatorFactory)

    override suspend fun invoke(userId: Uuid, draft: AppointmentRequestDraft): Result<Unit> = runSuspendCatching {
        if (!requestDataSource.reserveOfferToken(draft.offerToken)) throw BookingQuoteError.TokenAlreadyUsed()
        runCleaningUpOnFailure(cleanUp = { releaseOfferTokenBestEffort(draft.offerToken) }) {
            book(userId, draft)
        }
    }

    private suspend fun book(userId: Uuid, draft: AppointmentRequestDraft) {
        val quoteOffer = quoteVerifier.verify(draft.businessId, draft.services, draft.offerToken)

        val context = businessClient
            .getAppointmentBookingContext(
                businessId = draft.businessId,
                employeeId = draft.employeeId,
                userId = userId,
                serviceIds = draft.services.map { it.serviceId },
            )
            .getOrThrow()

        val request = buildAppointmentRequest(userId, draft, context)
        quoteVerifier.requireUnchanged(quoteOffer, request.totalAmount, request.dateEnd - request.date)

        createOrRequestAppointment(userId, request).getOrThrow()
    }

    private suspend fun createOrRequestAppointment(
        userId: Uuid,
        request: AppointmentRequest,
    ): Result<Unit> = transactionManager.transaction<Unit> {
        if (deletedUserDataSource.isDeletedForShare(userId)) throw Error.OperationNotAllowed()
        if (deletedClientDataSource.isDeletedForShare(
                request.businessId,
                request.client.id,
            )
        ) {
            throw Error.OperationNotAllowed()
        }
        val settings = settingsDataSource.getForUpdate(request.businessId) ?: throw Error.NotFound()

        if (settings.automaticApproval) {
            return@transaction createAppointment.createBypassingPermissions(request)
                .map { Unit }
                .getOrThrow()
        }

        assertRequestIsSchedulable(request, settings)

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

    private suspend fun assertRequestIsSchedulable(request: AppointmentRequest, settings: AppointmentSettings) {
        if (request.date < Clock.System.now()) throw DateInThePastNotAllowed()
        if (!settings.isInWorkday(request.date)) throw RequestForThisDateNotAllowed()
        if (!settings.isInWorktime(request.date, request.dateEnd)) throw RequestForThisTimeNotAllowed()
        if (requestDataSource.hasOverlapsWith(request)) throw RequestForThisTimeExists()
        if (appointmentDataSource.hasOverlapsWith(request)) throw RequestForThisTimeExists()
    }

    private suspend fun sendRequestCreatedNotification(request: AppointmentRequest) {
        val business = subscriptionDataSource.getBusinessSnapshot(request.businessId) ?: run {
            createAppointmentRequestLogger.error("No business with id ${request.businessId} exists")
            throw Error.NotFound()
        }
        eventProducer.send(
            AppointmentEvent.RequestCreated(
                clientUserId = request.client.userId,
                clientName = request.client.fullName,
                employeeUserId = request.employee.userId,
                employeeName = request.employee.fullName,
                from = request.date,
                to = request.dateEnd,
                timeZone = business.timeZone,
                businessName = business.name,
                address = business.address,
                price = moneyFormatter.print(request.totalAmount),
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
        client = context.clientSnapshot(),
        services = context.serviceSnapshots(draft.services),
        status = AppointmentRequestStatus.PENDING,
        date = draft.date,
        note = draft.note,
        declineReason = "",
    )
}
