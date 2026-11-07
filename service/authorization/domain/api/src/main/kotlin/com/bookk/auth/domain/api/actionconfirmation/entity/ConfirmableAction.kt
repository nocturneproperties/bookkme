package com.bookk.auth.domain.api.actionconfirmation.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class ConfirmableAction {
    @ProtoNumber(0)
    DELETE_CLIENT,

    @ProtoNumber(1)
    EXPORT_PERSONAL_DATA,

    @ProtoNumber(2)
    EXPORT_BUSINESS_DATA,

    @ProtoNumber(3)
    DELETE_ACCOUNT,
}
