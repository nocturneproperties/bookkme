package com.bookk.business.domain.api.business.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class UserBusinesses(
    @ProtoNumber(1) val dashboardId: Uuid?,
    @ProtoNumber(2) val businesses: List<Business>
)