package com.bookk.auth.domain.api.delete_account.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class DeleteAccountRequest(
    @ProtoNumber(3) val confirmationToken: String = "",
)
