package com.bookk.business.domain.api.business.entity

import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BusinessCreateRequest(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val currencyCode: String,
    @ProtoNumber(3) val timeZone: TimeZone
)
