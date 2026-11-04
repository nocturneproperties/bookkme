package com.bookk.notifications.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class NotificationSettingsUpdate(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val appointmentEnabled: Boolean,
    @ProtoNumber(3) val channels: List<NotificationChannelSettings>,
)
