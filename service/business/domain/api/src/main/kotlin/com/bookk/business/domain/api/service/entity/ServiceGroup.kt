package com.bookk.business.domain.api.service.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class ServiceGroup(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val businessId: Uuid,
    @ProtoNumber(3) val name: String,
    @ProtoNumber(4) val createdAt: Instant
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            businessId: Uuid = Uuid.random(),
            name: String = "stub-group",
            createdAt: Instant = Instant.fromEpochMilliseconds(0)
        ) = ServiceGroup(id = id, businessId = businessId, name = name, createdAt = createdAt)
    }
}