package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.SavedBusiness
import kotlin.uuid.Uuid

interface GetSavedBusinesses {
    suspend operator fun invoke(userId: Uuid): Result<List<SavedBusiness>>
}
