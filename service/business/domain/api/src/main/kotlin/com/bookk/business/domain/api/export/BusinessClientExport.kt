package com.bookk.business.domain.api.export

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class BusinessClientExport(
    val id: Uuid,
    val name: String,
    val lastName: String,
    val phone: String?,
    val email: String?,
    val description: String?,
    val hasAccount: Boolean,
    val lastActivityAt: Instant,
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            name: String = "Ivan",
            lastName: String = "Petrenko",
            phone: String? = null,
            email: String? = null,
            description: String? = null,
            hasAccount: Boolean = false,
            lastActivityAt: Instant = Instant.fromEpochMilliseconds(0),
        ) = BusinessClientExport(id, name, lastName, phone, email, description, hasAccount, lastActivityAt)
    }
}
