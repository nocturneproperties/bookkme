package com.bookk.core.domain.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class PaginationMetadata(
    @ProtoNumber(1) val total: Long,
    @ProtoNumber(2) val page: Long,
    @ProtoNumber(3) val pageSize: Int,
)