package library.email.impl

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
internal data class EmailCodeSession(
    @ProtoNumber(1) val ownerId: Uuid,
    @ProtoNumber(2) val email: String,
    @ProtoNumber(3) val purpose: String,
    @ProtoNumber(4) val codeHash: String,
)
