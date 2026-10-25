package com.bookk.appointments.domain.datasource

import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface AppointmentSubscriptionDataSource {
    suspend fun getBusinessSnapshot(id: Uuid): BusinessSnapshot?
    suspend fun attachBusiness(snapshot: BusinessSnapshot)

    suspend fun updateBusiness(snapshot: BusinessSnapshot, updatedAt: Instant)
    suspend fun detachBusiness(businessId: Uuid)
    suspend fun enableBusiness(businessId: Uuid)
    suspend fun isBusinessEnabled(businessId: Uuid): Boolean
    suspend fun disableBusiness(businessId: Uuid)
}
