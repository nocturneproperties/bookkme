package com.bookk.auth.domain.api.registration.entity

import com.bookk.auth.domain.api.authentication.entity.FinishRegistrationRequest
import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class VerifyAccountCreationRequest(
    @ProtoNumber(1) override val requestId: String,
    @ProtoNumber(2) val deviceInfo: DeviceInfo,
    @ProtoNumber(3) val userInfo: UserInfo,
    @ProtoNumber(4) override val publicKeyCredentialJson: String,
    @ProtoNumber(5) val acceptedDocuments: AcceptedLegalDocuments,
    @ProtoNumber(6) val verificationId: Uuid,
    @ProtoNumber(7) val emailCode: String,
) : FinishRegistrationRequest {
    @Serializable
    data class UserInfo(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val lastName: String,
        @ProtoNumber(3) val email: String,
    )

    @Serializable
    data class DeviceInfo(
        @ProtoNumber(1) val deviceUUID: Uuid,
        @ProtoNumber(2) val deviceName: String,
    )
}
