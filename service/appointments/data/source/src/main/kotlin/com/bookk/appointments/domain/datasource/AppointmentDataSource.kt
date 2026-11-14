package com.bookk.appointments.domain.datasource

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentPagination
import com.bookk.appointments.domain.api.entity.AppointmentRepresentation
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.PriceAdjustment
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface AppointmentDataSource {
    suspend fun create(request: AppointmentRequest): Appointment
    suspend fun create(appointment: Appointment): Appointment
    suspend fun get(id: Uuid): Appointment
    suspend fun getForUpdate(id: Uuid): Appointment
    suspend fun getAll(businessId: Uuid): List<Appointment>
    suspend fun getAllByIds(ids: Collection<Uuid>): List<Appointment>
    suspend fun getAllForDate(businessId: Uuid, range: ClosedRange<Instant>, employeeId: Uuid?): List<Appointment>
    suspend fun getAllPaginated(
        businessId: Uuid,
        limit: Int,
        offset: Long,
        query: String? = null,
    ): AppointmentPagination
    suspend fun update(appointment: Appointment): Appointment
    suspend fun delete(id: Uuid)
    suspend fun hasOverlapsWith(appointment: AppointmentRepresentation): Boolean
    suspend fun cancel(id: Uuid, reason: String): Appointment
    suspend fun cancelAll(ids: Collection<Uuid>, reason: String)
    suspend fun markCompleted(now: Instant)
    suspend fun markUnresolved(before: Instant)
    suspend fun markCompletedByUser(id: Uuid): Appointment
    suspend fun reopen(id: Uuid)
    suspend fun adjustPrice(id: Uuid, adjustment: PriceAdjustment): Appointment
    suspend fun markNoShow(id: Uuid): Appointment
    suspend fun anonymizeEmployee(employeeUserId: Uuid)
    suspend fun getClientIdsOfUserForUpdate(clientUserId: Uuid): Set<Uuid>
    suspend fun anonymizeClientUser(clientUserId: Uuid)
    suspend fun unlinkInitiator(initiatorUserId: Uuid)
    suspend fun getUpcomingIdsOfUserForUpdate(userId: Uuid, startingAfter: Instant): List<Uuid>
    suspend fun getUpcomingIdsOfClientForUpdate(businessId: Uuid, clientId: Uuid, startingAfter: Instant): List<Uuid>
    suspend fun anonymizeClient(businessId: Uuid, clientId: Uuid)
    suspend fun reassignClients(businessId: Uuid, fromClientIds: Collection<Uuid>, toClientId: Uuid)
    suspend fun linkClientUser(businessId: Uuid, clientId: Uuid, userId: Uuid)
}
