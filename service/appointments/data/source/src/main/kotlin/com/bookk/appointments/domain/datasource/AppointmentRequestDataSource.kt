package com.bookk.appointments.domain.datasource

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface AppointmentRequestDataSource {

    suspend fun get(id: Uuid): AppointmentRequest?
    suspend fun getForUpdate(id: Uuid): AppointmentRequest?
    suspend fun getPending(businessId: Uuid): List<AppointmentRequest>
    suspend fun getPendingByUser(userId: Uuid, businessId: Uuid): List<AppointmentRequest>
    suspend fun create(request: AppointmentRequest): AppointmentRequest
    suspend fun update(request: AppointmentRequest): AppointmentRequest
    suspend fun delete(request: AppointmentRequest)
    suspend fun approve(request: AppointmentRequest)
    suspend fun decline(id: Uuid, reason: String): AppointmentRequest

    suspend fun hasOverlapsWith(request: AppointmentRequest): Boolean
    suspend fun cancelOutdated(before: Instant)
    suspend fun deleteForUser(userId: Uuid)

    suspend fun reserveOfferToken(token: String): Boolean
    suspend fun releaseOfferToken(token: String)
}