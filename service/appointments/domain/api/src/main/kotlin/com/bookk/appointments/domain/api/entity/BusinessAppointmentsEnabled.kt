package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class BusinessAppointmentsEnabled(
    @ProtoNumber(1) val businessId: Uuid,
    @ProtoNumber(2) val enabled: Boolean
) {
    companion object {
        fun stub(
            businessId: Uuid = Uuid.random(),
            enabled: Boolean = true
        ) = BusinessAppointmentsEnabled(businessId = businessId, enabled = enabled)
    }
}
