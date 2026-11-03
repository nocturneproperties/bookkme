package com.bookk.notifications.client.api.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class NotificationsUserDataExport(
    @ProtoNumber(1) val settings: SettingsExport?,
    @ProtoNumber(2) val devices: List<DeviceExport>,
    @ProtoNumber(3) val email: String?,
    @ProtoNumber(4) val telegram: String?,
) {
    @Serializable
    data class SettingsExport(
        @ProtoNumber(1) val appointmentNotificationsEnabled: Boolean,
        @ProtoNumber(2) val channels: List<ChannelExport>,
    )

    @Serializable
    data class ChannelExport(
        @ProtoNumber(1) val channel: String,
        @ProtoNumber(2) val enabled: Boolean,
    )

    @Serializable
    data class DeviceExport(
        @ProtoNumber(1) val language: String,
        @ProtoNumber(2) val pushNotificationsRegistered: Boolean,
    )

    companion object {
        fun stub(
            settings: SettingsExport? = null,
            devices: List<DeviceExport> = emptyList(),
            email: String? = null,
            telegram: String? = null,
        ) = NotificationsUserDataExport(settings, devices, email, telegram)
    }
}
