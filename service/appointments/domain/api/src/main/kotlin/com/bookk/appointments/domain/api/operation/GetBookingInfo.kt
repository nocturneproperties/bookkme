package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.BookingInfo
import kotlin.uuid.Uuid

interface GetBookingInfo {
    suspend operator fun invoke(businessId: Uuid): Result<BookingInfo>
}
