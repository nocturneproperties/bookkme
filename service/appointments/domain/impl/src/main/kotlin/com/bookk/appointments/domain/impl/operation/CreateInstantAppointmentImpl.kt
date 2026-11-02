package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.BookingQuoteError
import com.bookk.appointments.domain.api.entity.InstantAppointmentDraft
import com.bookk.appointments.domain.api.operation.CreateInstantAppointment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.DeletedClientDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.runCleaningUpOnFailure
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.api.BusinessClient
import library.permissions.PermissionAction
import library.permissions.assertOrSelf
import library.signing.TokenValidatorFactory
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.uuid.Uuid

private val createInstantAppointmentLogger = LoggerFactory.getLogger(CreateInstantAppointmentImpl::class.java)

internal class CreateInstantAppointmentImpl(
    private val requestDataSource: AppointmentRequestDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val appointmentDataSource: AppointmentDataSource,
    private val settingsDataSource: AppointmentSettingsDataSource,
    private val transactionManager: TransactionManager,
    tokenValidatorFactory: TokenValidatorFactory,
    private val businessClient: BusinessClient,
    private val deletedUserDataSource: DeletedUserDataSource,
    private val deletedClientDataSource: DeletedClientDataSource,
) : CreateInstantAppointment {

    private val quoteVerifier = BookingQuoteVerifier(tokenValidatorFactory)

    override suspend fun invoke(userId: Uuid, draft: InstantAppointmentDraft): Result<Appointment> = runSuspendCatching {
        if (!requestDataSource.reserveOfferToken(draft.offerToken)) throw BookingQuoteError.TokenAlreadyUsed()
        runCleaningUpOnFailure(cleanUp = { releaseOfferTokenBestEffort(draft.offerToken) }) {
            book(userId, draft)
        }
    }

    private suspend fun book(userId: Uuid, draft: InstantAppointmentDraft): Appointment {
        val quoteOffer = quoteVerifier.verify(draft.businessId, draft.services, draft.offerToken)

        val context = businessClient
            .getStaffBookingContext(
                businessId = draft.businessId,
                employeeId = draft.employeeId,
                clientId = draft.clientId,
                serviceIds = draft.services.map { it.serviceId },
            )
            .getOrThrow()

        assertMayBookInto(userId, draft.businessId, context.employee.userId)

        val appointment = buildAppointment(userId, draft, context)
        quoteVerifier.requireUnchanged(quoteOffer, appointment.totalAmount, appointment.dateEnd - appointment.date)

        return transactionManager.transaction {
            val clientUserId = appointment.client.userId
            if (clientUserId != null && deletedUserDataSource.isDeletedForShare(clientUserId)) throw Error.OperationNotAllowed()
            if (deletedClientDataSource.isDeletedForShare(appointment.businessId, appointment.client.id)) throw Error.OperationNotAllowed()
            requireBookable(appointment)
            appointmentDataSource.create(appointment)
        }.getOrThrow()
    }

    private suspend fun requireBookable(appointment: Appointment) {
        val settings = settingsDataSource.getForUpdate(appointment.businessId) ?: throw Error.NotFound()
        if (appointment.date < Clock.System.now()) throw CreateInstantAppointment.Error.DateInThePastNotAllowed()
        if (!settings.isInWorkday(appointment.date)) throw CreateInstantAppointment.Error.RequestForThisDateNotAllowed()
        if (!settings.isInWorktime(appointment.date, appointment.dateEnd)) throw CreateInstantAppointment.Error.RequestForThisTimeNotAllowed()
        if (appointmentDataSource.hasOverlapsWith(appointment)) throw CreateInstantAppointment.Error.AppointmentForThisTimeExists()
    }

    private suspend fun assertMayBookInto(userId: Uuid, businessId: Uuid, employeeUserId: Uuid) {
        transactionManager.transaction {
            appointmentPermissionDataSource.getPermission(userId, businessId)
                .assertOrSelf(PermissionAction.UPDATE, actorId = userId, assigneeId = employeeUserId)
        }.getOrThrow()
    }

    private fun buildAppointment(
        userId: Uuid,
        draft: InstantAppointmentDraft,
        context: AppointmentBookingContext,
    ) = Appointment(
        id = Uuid.random(),
        initiatorUserId = userId,
        businessId = draft.businessId,
        employee = context.employeeSnapshot(),
        client = context.clientSnapshot(),
        services = context.serviceSnapshots(draft.services),
        status = AppointmentStatus.SCHEDULED,
        date = draft.date,
        note = draft.note,
        cancellationReason = "",
        completedBy = null,
        priceAdjustment = null,
    )

    private suspend fun releaseOfferTokenBestEffort(offerToken: String) {
        runSuspendCatching { requestDataSource.releaseOfferToken(offerToken) }
            .onFailure { createInstantAppointmentLogger.error("Failed to release offer token, it stays unusable until it expires", it) }
    }
}
