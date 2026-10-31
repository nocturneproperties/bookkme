package com.bookk.business.domain.api.client.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class ClientImportResult(
    @ProtoNumber(1) val created: List<ClientRemote>,
    @ProtoNumber(2) val rejected: List<RejectedClient>
) {
    companion object {
        fun stub(
            created: List<ClientRemote> = listOf(ClientRemote.stub()),
            rejected: List<RejectedClient> = emptyList()
        ) = ClientImportResult(created = created, rejected = rejected)
    }
}

@Serializable
data class RejectedClient(
    @ProtoNumber(1) val index: Int,
    @ProtoNumber(2) val errorCode: Int
) {
    companion object {
        fun stub(index: Int = 0, errorCode: Int = 0) = RejectedClient(index = index, errorCode = errorCode)
    }
}
