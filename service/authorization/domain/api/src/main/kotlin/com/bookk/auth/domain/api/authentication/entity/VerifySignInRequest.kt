package com.bookk.auth.domain.api.authentication.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class VerifySignInRequest(
    @ProtoNumber(1) override val requestId: String,
    @ProtoNumber(2) override val publicKeyCredentialJson: String,
    @ProtoNumber(3) val deviceInfo: DeviceInfo
): FinishAssertionRequest {
    @Serializable
    data class DeviceInfo(
        @ProtoNumber(1) val deviceUUID: Uuid,
        @ProtoNumber(2) val deviceName: String
    )
}