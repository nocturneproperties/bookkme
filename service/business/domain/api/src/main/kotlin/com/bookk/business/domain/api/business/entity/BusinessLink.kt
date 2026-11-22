package com.bookk.business.domain.api.business.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class BusinessLink(
    @ProtoNumber(1) val businessId: Uuid,
    @ProtoNumber(2) val name: String,
) {
    companion object {
        fun stub(businessId: Uuid = Uuid.random(), name: String = "business-link"): BusinessLink {
            return BusinessLink(businessId = businessId, name = name)
        }
    }
}
