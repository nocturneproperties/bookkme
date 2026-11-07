package com.bookk.user.domain.api.export

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class ProfileExport(
    val id: Uuid,
    val name: String,
    val lastName: String,
    val email: String,
    val phone: String?,
    val createdAt: Instant,
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            name: String = "Olena",
            lastName: String = "Koval",
            email: String = "olena@example.com",
            phone: String? = null,
            createdAt: Instant = Instant.fromEpochMilliseconds(0),
        ) = ProfileExport(id, name, lastName, email, phone, createdAt)
    }
}
