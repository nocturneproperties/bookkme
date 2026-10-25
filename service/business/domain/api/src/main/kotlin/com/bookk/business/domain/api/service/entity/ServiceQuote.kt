package com.bookk.business.domain.api.service.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class ServiceQuote(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val services: List<Service>,
    @ProtoNumber(3) val token: String
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            services: List<Service> = emptyList(),
            token: String = "stub-quote-token"
        ) = ServiceQuote(id = id, services = services, token = token)
    }
}
