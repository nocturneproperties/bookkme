package com.bookk.business.domain.api.client.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class ClientUpdateModel(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val name: String?,
    @ProtoNumber(3) val lastName: String?,
    @ProtoNumber(4) val phone: String?,
    @ProtoNumber(5) val email: String?,
    @ProtoNumber(6) val description: String?
)
