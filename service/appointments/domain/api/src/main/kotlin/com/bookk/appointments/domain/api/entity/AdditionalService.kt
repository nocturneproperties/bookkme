package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class AdditionalService(
    @ProtoNumber(1) val service: ServiceSnapshot,
    @ProtoNumber(2) val count: Int,
) {
    companion object {
        fun stub(
            service: ServiceSnapshot = ServiceSnapshot.stub(),
            count: Int = 1,
        ): AdditionalService {
            return AdditionalService(service = service, count = count)
        }
    }
}
