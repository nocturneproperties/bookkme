package com.bookk.auth.domain.api.demo.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class DemoUpgradeStartRequest(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val email: String,
    @ProtoNumber(3) val refreshToken: String?,
) {
    companion object {
        fun stub(
            name: String = "Jane Smith",
            email: String = "jane@example.com",
            refreshToken: String? = null,
        ): DemoUpgradeStartRequest {
            return DemoUpgradeStartRequest(name, email, refreshToken)
        }
    }
}
