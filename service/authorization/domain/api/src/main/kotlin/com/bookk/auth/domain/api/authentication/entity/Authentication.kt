package com.bookk.auth.domain.api.authentication.entity

import kotlin.uuid.Uuid

data class Authentication(
    val id: Uuid,
    val userId: Uuid,
    val uuid: Uuid
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            userId: Uuid = Uuid.random(),
            uuid: Uuid = Uuid.random()
        ) = Authentication(id, userId, uuid)
    }
}