package com.bookk.business.domain.api.client.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class ErasedClient(
    @ProtoNumber(1) val businessId: Uuid,
    @ProtoNumber(2) val clientId: Uuid,
) {
    companion object {
        fun stub(
            businessId: Uuid = Uuid.random(),
            clientId: Uuid = Uuid.random(),
        ) = ErasedClient(businessId = businessId, clientId = clientId)
    }
}
