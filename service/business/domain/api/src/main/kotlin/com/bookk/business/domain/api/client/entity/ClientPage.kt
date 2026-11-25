package com.bookk.business.domain.api.client.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class ClientPage(
    @ProtoNumber(1) val data: List<ClientRemote>,
    @ProtoNumber(2) val nextCursor: String?,
)
