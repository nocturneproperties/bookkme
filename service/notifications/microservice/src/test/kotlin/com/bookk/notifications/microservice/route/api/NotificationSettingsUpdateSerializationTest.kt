package com.bookk.notifications.microservice.route.api

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.api.entity.NotificationChannelSettings
import com.bookk.notifications.domain.api.entity.NotificationSettingsUpdate
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test
import kotlin.uuid.Uuid

internal class NotificationSettingsUpdateSerializationTest {
    @Test
    fun `should preserve the legacy notification settings update protobuf format`() = runUnitTest {
        given()
        val settingsId = "00000000-0000-0000-0000-000000000001"
        val channelId = "00000000-0000-0000-0000-000000000002"
        val legacyPayload = byteArrayOf(0x0a, 0x24) + settingsId.encodeToByteArray() +
            byteArrayOf(0x10, 0x01, 0x1a, 0x2c, 0x0a, 0x24) + channelId.encodeToByteArray() +
            byteArrayOf(0x10, 0x01, 0x18, 0x00, 0x20, 0x01)
        val update = NotificationSettingsUpdate(
            id = Uuid.parse(settingsId),
            appointmentEnabled = true,
            channels = listOf(
                NotificationChannelSettings.stub(
                    id = Uuid.parse(channelId),
                    channel = CommunicationChannel.EMAIL,
                    enabled = false,
                    availableToClients = true,
                ),
            ),
        )
        val protobuf = ProtoBuf { encodeDefaults = true }

        whenn()
        val encoded = protobuf.encodeToByteArray(NotificationSettingsUpdate.serializer(), update)
        val decoded = protobuf.decodeFromByteArray(NotificationSettingsUpdate.serializer(), legacyPayload)

        then()
        assertArrayEquals(legacyPayload, encoded)
        assertEquals(update, decoded)
    }
}
