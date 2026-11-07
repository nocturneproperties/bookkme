package com.bookk.user.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class LegalDocument {
    @ProtoNumber(0)
    PRIVACY_POLICY,

    @ProtoNumber(1)
    TERMS_OF_SERVICE,
}
