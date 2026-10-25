package com.bookk.business.domain.api.business.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class BusinessResource {
    @ProtoNumber(0) BUSINESS,
    @ProtoNumber(1) EMPLOYEES,
    @ProtoNumber(2) CLIENTS,
    @ProtoNumber(3) SERVICES,
    @ProtoNumber(4) APPOINTMENTS
}
