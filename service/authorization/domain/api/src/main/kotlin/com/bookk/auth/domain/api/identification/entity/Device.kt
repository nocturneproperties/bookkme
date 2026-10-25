package com.bookk.auth.domain.api.identification.entity

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.token.entity.SafeRefreshToken
import kotlin.time.Instant
import kotlin.uuid.Uuid

class Device(
    val authRecord: Authentication,
    val deviceInfo: DeviceInfo
)

class DeviceInfo(
    val id: Uuid,
    val deviceUUID: Uuid,
    val refreshToken: SafeRefreshToken?,
    val previousRefreshToken: SafeRefreshToken?,
    val deviceName: String,
    val isSignedIn: Boolean,
    val lastLogInAt: Instant? = null
)