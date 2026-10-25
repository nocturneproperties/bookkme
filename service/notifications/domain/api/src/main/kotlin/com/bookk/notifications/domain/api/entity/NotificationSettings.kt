package com.bookk.notifications.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class NotificationSettings(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val userId: Uuid,
    @ProtoNumber(3) val appointmentEnabled: Boolean,
    @ProtoNumber(4) val channels: List<NotificationChannelSettings>,
) {

    @Serializable
    data class Update(
        @ProtoNumber(1) val id: Uuid,
        @ProtoNumber(2) val appointmentEnabled: Boolean,
        @ProtoNumber(3) val channels: List<NotificationChannelSettings>,
    )

    constructor(userId: Uuid) : this(
        id = Uuid.random(),
        userId = userId,
        appointmentEnabled = false,
        channels = listOf(
            NotificationChannelSettings(
                id = Uuid.random(),
                channel = CommunicationChannel.PUSH_NOTIFICATIONS,
                enabled = false,
                availableToClients = true
            ),
            NotificationChannelSettings(
                id = Uuid.random(),
                channel = CommunicationChannel.EMAIL,
                enabled = false,
                availableToClients = false
            ),
            NotificationChannelSettings(
                id = Uuid.random(),
                channel = CommunicationChannel.TELEGRAM,
                enabled = false,
                availableToClients = false
            )
        )
    )

    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            userId: Uuid = Uuid.random(),
            appointmentEnabled: Boolean = true,
            channels: List<NotificationChannelSettings> = emptyList(),
        ) = NotificationSettings(id, userId, appointmentEnabled, channels)
    }
}
