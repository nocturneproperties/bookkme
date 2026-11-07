package com.bookk.user.domain.impl.export

import com.bookk.business.domain.api.export.BusinessUserDataExport
import com.bookk.notifications.client.api.export.NotificationsUserDataExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport
import com.bookk.server.auth.client.export.AccountDataExport
import com.bookk.user.domain.api.export.ContactMessageExport
import com.bookk.user.domain.api.export.ProfileExport
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
internal data class PersonalDataExport(
    val formatVersion: Int,
    val generatedAt: Instant,
    val userId: Uuid,
    val profile: ProfileExport,
    val contactMessages: List<ContactMessageExport>,
    val legalAcceptances: List<LegalAcceptanceExport>,
    val account: AccountDataExport,
    val businesses: BusinessUserDataExport,
    val appointments: AppointmentsUserDataExport,
    val notifications: NotificationsUserDataExport,
) {
    @Serializable
    data class LegalAcceptanceExport(
        val document: String,
        val version: String,
        val acceptedAt: Instant,
    )

    companion object {
        const val FORMAT_VERSION = 1
    }
}
