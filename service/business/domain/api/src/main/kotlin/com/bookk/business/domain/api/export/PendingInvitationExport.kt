package com.bookk.business.domain.api.export

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class PendingInvitationExport(
    val id: Uuid,
    val invitedBy: Uuid,
    val createdAt: Instant,
)
