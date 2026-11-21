package com.bookk.appointments.domain.datasource

import com.bookk.appointments.domain.api.entity.SavedBusiness
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface SavedBusinessDataSource {
    suspend fun isSaved(userId: Uuid, businessId: Uuid): Boolean
    suspend fun count(userId: Uuid): Long
    suspend fun save(userId: Uuid, businessId: Uuid, savedAt: Instant)
    suspend fun remove(userId: Uuid, businessId: Uuid)
    suspend fun getAll(userId: Uuid): List<SavedBusiness>
    suspend fun getSavedAt(userId: Uuid): Map<Uuid, Instant>
    suspend fun deleteForUser(userId: Uuid)
}
