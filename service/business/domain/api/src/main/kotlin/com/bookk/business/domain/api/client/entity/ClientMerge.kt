package com.bookk.business.domain.api.client.entity

import kotlin.uuid.Uuid

data class ClientMerge(
    val businessId: Uuid,
    val fromClientId: Uuid,
    val toClientId: Uuid
) {
    companion object {
        fun stub(
            businessId: Uuid = Uuid.random(),
            fromClientId: Uuid = Uuid.random(),
            toClientId: Uuid = Uuid.random()
        ) = ClientMerge(businessId = businessId, fromClientId = fromClientId, toClientId = toClientId)
    }
}
