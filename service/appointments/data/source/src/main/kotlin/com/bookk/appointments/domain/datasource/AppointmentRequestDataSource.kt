package com.bookk.appointments.domain.datasource

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestHistoryFilter
import com.bookk.appointments.domain.api.entity.StartBounds
import com.bookk.core.domain.entity.Page
import com.bookk.core.domain.entity.PageRequest
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface AppointmentRequestDataSource {

    suspend fun get(id: Uuid): AppointmentRequest?
    suspend fun getForUpdate(id: Uuid): AppointmentRequest?
    suspend fun getPending(businessId: Uuid): List<AppointmentRequest>
    suspend fun getPendingByUser(userId: Uuid, businessId: Uuid): List<AppointmentRequest>
    suspend fun getHistoryPage(
        businessId: Uuid,
        filter: AppointmentRequestHistoryFilter,
        starts: StartBounds,
        page: PageRequest,
    ): Page<AppointmentRequest>
    suspend fun getUpcomingPendingOfUser(userId: Uuid, startingAfter: Instant, limit: Int): List<AppointmentRequest>
    suspend fun countPendingByUser(userId: Uuid, businessId: Uuid): Long
    suspend fun create(request: AppointmentRequest): AppointmentRequest
    suspend fun update(request: AppointmentRequest): AppointmentRequest
    suspend fun delete(request: AppointmentRequest)
    suspend fun approve(request: AppointmentRequest)
    suspend fun decline(id: Uuid, reason: String): AppointmentRequest
    suspend fun withdraw(id: Uuid)

    suspend fun cancelOutdated(before: Instant)
    suspend fun getClientIdsOfUserForUpdate(clientUserId: Uuid): Set<Uuid>
    suspend fun deleteForUser(userId: Uuid)
    suspend fun deleteForClient(businessId: Uuid, clientId: Uuid)
    suspend fun reassignClients(businessId: Uuid, fromClientIds: Collection<Uuid>, toClientId: Uuid)
    suspend fun linkClientUser(businessId: Uuid, clientId: Uuid, userId: Uuid)

    suspend fun reserveOfferToken(token: String): Boolean
    suspend fun releaseOfferToken(token: String)
    suspend fun deleteEndedBefore(before: Instant)
}
