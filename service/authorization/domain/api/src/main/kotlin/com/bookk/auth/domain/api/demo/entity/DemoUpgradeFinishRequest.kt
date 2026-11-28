package com.bookk.auth.domain.api.demo.entity

import com.bookk.auth.domain.api.authentication.entity.FinishRegistrationRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class DemoUpgradeFinishRequest(
    @ProtoNumber(1) override val requestId: String,
    @ProtoNumber(2) override val publicKeyCredentialJson: String,
    @ProtoNumber(3) val verificationId: Uuid,
    @ProtoNumber(4) val emailCode: String,
    @ProtoNumber(5) val name: String,
    @ProtoNumber(6) val refreshToken: String?,
) : FinishRegistrationRequest {
    companion object {
        fun stub(
            requestId: String = Uuid.random().toString(),
            publicKeyCredentialJson: String = "{}",
            verificationId: Uuid = Uuid.random(),
            emailCode: String = "123456",
            name: String = "Jane Smith",
            refreshToken: String? = null,
        ): DemoUpgradeFinishRequest {
            return DemoUpgradeFinishRequest(
                requestId = requestId,
                publicKeyCredentialJson = publicKeyCredentialJson,
                verificationId = verificationId,
                emailCode = emailCode,
                name = name,
                refreshToken = refreshToken,
            )
        }
    }
}
