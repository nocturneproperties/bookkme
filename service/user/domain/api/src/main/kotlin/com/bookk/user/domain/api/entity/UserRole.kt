package com.bookk.user.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class UserRole(val id: Int) {
    @ProtoNumber(0) BUSINESS_OWNER(1),
    @ProtoNumber(1) CLIENT(2),
    @ProtoNumber(2) EMPLOYEE(3)
}