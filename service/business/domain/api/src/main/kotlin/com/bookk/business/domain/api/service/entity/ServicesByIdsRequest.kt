package com.bookk.business.domain.api.service.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class ServicesByIdsRequest(
    @ProtoNumber(1) val serviceIds: List<Uuid>
) {
    companion object {
        fun stub(serviceIds: List<Uuid> = listOf(Uuid.random())) = ServicesByIdsRequest(serviceIds = serviceIds)
    }
}
