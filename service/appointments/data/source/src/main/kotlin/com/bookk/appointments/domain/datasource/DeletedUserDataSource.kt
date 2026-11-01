package com.bookk.appointments.domain.datasource

import kotlin.uuid.Uuid

interface DeletedUserDataSource {
    suspend fun markDeleted(userId: Uuid)
    suspend fun isDeletedForShare(userId: Uuid): Boolean
}
