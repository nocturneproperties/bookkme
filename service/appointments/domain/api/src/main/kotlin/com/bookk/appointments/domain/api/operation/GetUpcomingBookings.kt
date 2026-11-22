package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.UpcomingBookings
import kotlin.uuid.Uuid

interface GetUpcomingBookings {
    suspend operator fun invoke(userId: Uuid): Result<UpcomingBookings>

    companion object {
        const val LIMIT = 2
    }
}
