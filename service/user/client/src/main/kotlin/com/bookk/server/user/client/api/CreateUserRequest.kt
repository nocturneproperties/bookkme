package com.bookk.server.user.client.api

import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class CreateUserRequest(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val lastName: String,
    @ProtoNumber(3) val email: String,
    @ProtoNumber(4) val acceptedDocuments: AcceptedLegalDocuments,
)
