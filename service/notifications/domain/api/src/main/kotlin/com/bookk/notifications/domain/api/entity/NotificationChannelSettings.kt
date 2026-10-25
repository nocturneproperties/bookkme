package com.bookk.notifications.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class NotificationChannelSettings(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val channel: CommunicationChannel,
    @ProtoNumber(3) val enabled: Boolean,
    @ProtoNumber(4) val availableToClients: Boolean,
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            channel: CommunicationChannel = CommunicationChannel.EMAIL,
            enabled: Boolean = false,
            availableToClients: Boolean = true,
        ) = NotificationChannelSettings(id, channel, enabled, availableToClients)
    }
}
