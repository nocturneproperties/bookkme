package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class SavedBusiness(
    @ProtoNumber(1) val businessId: Uuid,
    @ProtoNumber(2) val enabled: Boolean,
    @ProtoNumber(3) val savedAt: Instant,
    @ProtoNumber(4) val lastVisitAt: Instant?,
) {
    companion object {
        fun stub(
            businessId: Uuid = Uuid.random(),
            enabled: Boolean = true,
            savedAt: Instant = Instant.fromEpochMilliseconds(0),
            lastVisitAt: Instant? = null,
        ): SavedBusiness {
            return SavedBusiness(
                businessId = businessId,
                enabled = enabled,
                savedAt = savedAt,
                lastVisitAt = lastVisitAt,
            )
        }
    }
}
