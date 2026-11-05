package com.bookk.auth.domain.api.identification.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class DeviceIdentity(
    @ProtoNumber(1) val authId: Uuid,
    @ProtoNumber(2) val deviceUuid: Uuid,
) {
    companion object {
        fun stub(authId: Uuid = Uuid.random(), deviceUuid: Uuid = Uuid.random()) = DeviceIdentity(authId, deviceUuid)
    }
}
