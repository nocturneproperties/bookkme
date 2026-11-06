package com.bookk.auth.domain.api.identification.entity

import kotlin.uuid.Uuid

data class DeviceIdentity(
    val authId: Uuid,
    val deviceUuid: Uuid,
) {
    companion object {
        fun stub(authId: Uuid = Uuid.random(), deviceUuid: Uuid = Uuid.random()) = DeviceIdentity(authId, deviceUuid)
    }
}
