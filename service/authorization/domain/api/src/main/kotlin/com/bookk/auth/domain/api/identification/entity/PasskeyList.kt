package com.bookk.auth.domain.api.identification.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class PasskeyList(
    @ProtoNumber(1) val rpId: String,
    @ProtoNumber(2) val userHandle: String,
    @ProtoNumber(3) val passkeys: List<PasskeyResponse>,
) {
    companion object {
        fun stub(
            rpId: String = "bookkme.app",
            userHandle: String = "dXNlcg",
            passkeys: List<PasskeyResponse> = listOf(PasskeyResponse.stub()),
        ): PasskeyList {
            return PasskeyList(rpId = rpId, userHandle = userHandle, passkeys = passkeys)
        }
    }
}
