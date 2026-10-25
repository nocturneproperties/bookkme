package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestDraft
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.ClientSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.appointments.domain.api.operation.CreateAppointment
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest.Error.DateInThePastNotAllowed
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest.Error.DurationChanged
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest.Error.PriceChanged
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest.Error.RequestForThisDateNotAllowed
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest.Error.RequestForThisTimeExists
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest.Error.RequestForThisTimeNotAllowed
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest.Error.ServicesSignatureMiss
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest.Error.TokenAlreadyUsed
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.library.serializer.moneyFormatter
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.business.client.api.BusinessClient
import com.bookk.server.business.client.api.QuoteClaims
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import library.signing.TokenValidatorFactory
import library.signing.ValidationType
import org.joda.money.Money
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.Uuid

private val createAppointmentRequestLogger = LoggerFactory.getLogger(CreateAppointmentRequestImpl::class.java)

private data class QuoteOffer(
    val businessId: String,
    val serviceCounts: Map<Uuid, Int>,
    val total: String,
    val duration: String
)

internal class CreateAppointmentRequestImpl(
    private val requestDataSource: AppointmentRequestDataSource,
    private val appointmentDataSource: AppointmentDataSource,
    private val settingsDataSource: AppointmentSettingsDataSource,
    private val subscriptionDataSource: AppointmentSubscriptionDataSource,
    private val eventProducer: StandardEventProducer,
    private val createAppointment: CreateAppointment,
    private val transactionManager: TransactionManager,
    private val tokenValidatorFactory: TokenValidatorFactory,
    private val businessClient: BusinessClient
) : CreateAppointmentRequest {

    override suspend fun invoke(userId: Uuid, draft: AppointmentRequestDraft): Result<Unit> = runSuspendCatching {
        if (!requestDataSource.reserveOfferToken(draft.offerToken)) throw TokenAlreadyUsed()
        try {
            book(userId, draft)
        } catch (failure: Throwable) {
            withContext(NonCancellable) { releaseOfferTokenBestEffort(draft.offerToken) }
            throw failure
        }
    }

    private suspend fun book(userId: Uuid, draft: AppointmentRequestDraft) {
        if (hasInvalidServicesSignature(draft)) throw ServicesSignatureMiss()

        val quoteOffer = decodeQuoteOffer(draft.offerToken) ?: throw ServicesSignatureMiss()
        if (!quoteOfferMatchesDraft(draft, quoteOffer)) throw ServicesSignatureMiss()

        val context = businessClient
            .getAppointmentBookingContext(
                businessId = draft.businessId,
                employeeId = draft.employeeId,
                userId = userId,
                serviceIds = draft.services.map { it.serviceId }
            )
            .getOrThrow()

        val request = buildAppointmentRequest(userId, draft, context)
        quoteMismatchError(request, quoteOffer)?.let { throw it }

        createOrRequestAppointment(userId, request).getOrThrow()
    }

    private fun hasInvalidServicesSignature(draft: AppointmentRequestDraft): Boolean {
        if (draft.services.any { it.count <= 0 }) return true
        val requestedServiceIds = draft.services.map { it.serviceId }
        return requestedServiceIds.distinct().size != requestedServiceIds.size
    }

    private fun decodeQuoteOffer(offerToken: String): QuoteOffer? = runCatching {
        val decodedOffer = tokenValidatorFactory.forType(ValidationType.SERVICE_QUOTE).verifier.verify(offerToken)
        QuoteOffer(
            businessId = decodedOffer.getClaim(QuoteClaims.CLAIM_BUSINESS_ID).asString(),
            serviceCounts = QuoteClaims.decodeServiceCounts(decodedOffer.getClaim(QuoteClaims.CLAIM_SERVICES).asList(String::class.java)),
            total = decodedOffer.getClaim(QuoteClaims.CLAIM_TOTAL).asString(),
            duration = decodedOffer.getClaim(QuoteClaims.CLAIM_DURATION).asString()
        )
    }.getOrNull()

    private fun quoteOfferMatchesDraft(draft: AppointmentRequestDraft, quoteOffer: QuoteOffer): Boolean {
        val requestedServiceCounts = draft.services.associate { it.serviceId to it.count }
        return requestedServiceCounts == quoteOffer.serviceCounts && draft.businessId.toString() == quoteOffer.businessId
    }

    private fun buildAppointmentRequest(userId: Uuid, draft: AppointmentRequestDraft, context: AppointmentBookingContext): AppointmentRequest {
        val servicesById = context.services.associateBy { it.id }
        val expandedServices = draft.services.flatMap { requested -> List(requested.count) { servicesById.getValue(requested.serviceId) } }

        return AppointmentRequest(
            id = Uuid.random(),
            userId = userId,
            businessId = draft.businessId,
            employee = EmployeeSnapshot(
                id = context.employee.id,
                userId = context.employee.userId,
                fullName = "${context.employee.name} ${context.employee.lastName}".trim()
            ),
            client = ClientSnapshot(
                id = context.client.id,
                fullName = "${context.client.name} ${context.client.lastName}".trim(),
                phone = context.client.phone,
                email = context.client.email
            ),
            services = expandedServices.map {
                ServiceSnapshot(id = it.id, name = it.name, groupId = it.group.id, price = it.price, duration = it.duration)
            },
            status = AppointmentRequestStatus.PENDING,
            date = draft.date,
            note = draft.note,
            declineReason = ""
        )
    }

    private fun quoteMismatchError(request: AppointmentRequest, quoteOffer: QuoteOffer): BusinessError? = when {
        request.totalAmount != Money.parse(quoteOffer.total) -> PriceChanged()
        request.dateEnd - request.date != Duration.parse(quoteOffer.duration) -> DurationChanged()
        else -> null
    }

    private suspend fun createOrRequestAppointment(userId: Uuid, request: AppointmentRequest): Result<Unit> =
        transactionManager.transaction<Unit> {
            val settings = settingsDataSource.getForUpdate(request.businessId) ?: throw Error.NotFound()

            if (settings.automaticApproval) {
                return@transaction createAppointment(userId, request)
                    .map { Unit }
                    .getOrThrow()
            }

            assertRequestIsSchedulable(request, settings)

            requestDataSource.create(request).also {
                sendRequestCreatedNotification(request)
            }
        }

    private suspend fun releaseOfferTokenBestEffort(offerToken: String) {
        runSuspendCatching { requestDataSource.releaseOfferToken(offerToken) }
            .onFailure { createAppointmentRequestLogger.error("Failed to release offer token, it stays unusable until it expires", it) }
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
                clientUserId = request.client.id,
                clientName = request.client.fullName,
                employeeUserId = request.employee.userId,
                employeeName = request.employee.fullName,
                from = request.date,
                to = request.dateEnd,
                timeZone = business.timeZone,
                businessName = business.name,
                address = business.address,
                price = moneyFormatter.print(request.totalAmount)
            )
        )
    }
}
