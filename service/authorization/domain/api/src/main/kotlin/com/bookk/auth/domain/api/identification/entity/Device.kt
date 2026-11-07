package com.bookk.auth.domain.api.identification.entity

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.token.entity.SafeRefreshToken
import kotlin.time.Instant
import kotlin.uuid.Uuid

class Device(
    val authRecord: Authentication,
    val deviceInfo: DeviceInfo,
) {
    companion object {
        fun stub(
            authRecord: Authentication = Authentication.stub(),
            deviceInfo: DeviceInfo = DeviceInfo.stub(),
        ) = Device(authRecord, deviceInfo)
    }
}

class DeviceInfo(
    val id: Uuid,
    val deviceUUID: Uuid,
    val refreshToken: SafeRefreshToken?,
    val previousRefreshToken: SafeRefreshToken?,
    val deviceName: String,
    val isSignedIn: Boolean,
    val lastLogInAt: Instant? = null,
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            deviceUUID: Uuid = Uuid.random(),
            refreshToken: SafeRefreshToken? = null,
            previousRefreshToken: SafeRefreshToken? = null,
            deviceName: String = "Pixel 9",
            isSignedIn: Boolean = true,
            lastLogInAt: Instant? = null,
        ) = DeviceInfo(id, deviceUUID, refreshToken, previousRefreshToken, deviceName, isSignedIn, lastLogInAt)
    }
}
