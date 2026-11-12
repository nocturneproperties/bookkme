package com.bookk.server.auth.client

import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class AuthEventTest {

    private val deviceUuid = Uuid.random()

    private fun languageUpdated(
        device: Uuid = deviceUuid,
        language: Language = Language.EN,
    ) = AuthEvent.DeviceLanguageUpdated(deviceUuid = device, language = language, authId = Uuid.random())

    @Test
    fun `should key the device language updated event by device id`() = runUnitTest {
        given()
        val event = languageUpdated()

        whenn()
        val partitionKey = event.partitionKey

        then()
        assertEquals(deviceUuid.toString(), partitionKey)
    }

    @Test
    fun `should route consecutive language updates of one device to the same partition key`() = runUnitTest {
        given()
        val first = languageUpdated()
        val second = languageUpdated()

        whenn()
        val keys = setOf(first.partitionKey, second.partitionKey)

        then()
        assertEquals(1, keys.size)
    }

    @Test
    fun `should route language updates of different devices to different partition keys`() = runUnitTest {
        given()
        val mine = languageUpdated()
        val other = languageUpdated(device = Uuid.random())

        whenn()
        val keys = setOf(mine.partitionKey, other.partitionKey)

        then()
        assertEquals(2, keys.size)
    }

    @Test
    fun `should keep the partition key out of the serialized form`() = runUnitTest {
        given()
        val descriptor = AuthEvent.DeviceLanguageUpdated.serializer().descriptor

        whenn()
        val serializedFields = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }

        then()
        assertFalse(serializedFields.contains("partitionKey"))
    }

    @Serializable
    private data class LegacyDeviceDeleted(
        @ProtoNumber(1) val deviceUuid: Uuid,
        @ProtoNumber(2) val idempotencyKey: String,
        @ProtoNumber(3) val topic: String = AuthEvent.DeviceDeleted.TOPIC,
    )

    @Serializable
    private data class LegacyDeviceLanguageUpdated(
        @ProtoNumber(1) val deviceUuid: Uuid,
        @ProtoNumber(2) val language: Language,
        @ProtoNumber(3) val idempotencyKey: String,
        @ProtoNumber(4) val topic: String = AuthEvent.DeviceLanguageUpdated.TOPIC,
    )

    @Test
    fun `should preserve existing wire fields and carry the account on device events`() = runUnitTest {
        given()
        val authId = Uuid.random()
        val uuid = Uuid.random()
        val deleted = AuthEvent.DeviceDeleted(uuid, authId = authId)
        val updated = AuthEvent.DeviceLanguageUpdated(uuid, Language.UK, authId = authId)
        val protoBuf = ProtoBuf { encodeDefaults = true }

        whenn()
        val deletionBytes = protoBuf.encodeToByteArray(AuthEvent.DeviceDeleted.serializer(), deleted)
        val updateBytes = protoBuf.encodeToByteArray(AuthEvent.DeviceLanguageUpdated.serializer(), updated)
        val legacyDeleted = protoBuf.decodeFromByteArray(LegacyDeviceDeleted.serializer(), deletionBytes)
        val legacyUpdated = protoBuf.decodeFromByteArray(LegacyDeviceLanguageUpdated.serializer(), updateBytes)

        then()
        assertEquals(LegacyDeviceDeleted(uuid, deleted.idempotencyKey), legacyDeleted)
        assertEquals(LegacyDeviceLanguageUpdated(uuid, Language.UK, updated.idempotencyKey), legacyUpdated)
        assertEquals(deleted, protoBuf.decodeFromByteArray(AuthEvent.DeviceDeleted.serializer(), deletionBytes))
        assertEquals(updated, protoBuf.decodeFromByteArray(AuthEvent.DeviceLanguageUpdated.serializer(), updateBytes))
    }

    @Test
    fun `should decode legacy device events with a missing account that the handler can reject`() = runUnitTest {
        given()
        val uuid = Uuid.random()
        val protoBuf = ProtoBuf { encodeDefaults = true }
        val deletionBytes = protoBuf.encodeToByteArray(
            LegacyDeviceDeleted.serializer(),
            LegacyDeviceDeleted(uuid, "old-delete"),
        )
        val updateBytes = protoBuf.encodeToByteArray(
            LegacyDeviceLanguageUpdated.serializer(),
            LegacyDeviceLanguageUpdated(uuid, Language.UK, "old-update"),
        )

        whenn()
        val deleted = protoBuf.decodeFromByteArray(AuthEvent.DeviceDeleted.serializer(), deletionBytes)
        val updated = protoBuf.decodeFromByteArray(AuthEvent.DeviceLanguageUpdated.serializer(), updateBytes)

        then()
        assertEquals(uuid, deleted.deviceUuid)
        assertEquals("old-delete", deleted.idempotencyKey)
        assertEquals(Uuid.NIL, deleted.authId)
        assertEquals(uuid, updated.deviceUuid)
        assertEquals("old-update", updated.idempotencyKey)
        assertEquals(Language.UK, updated.language)
        assertEquals(Uuid.NIL, updated.authId)
    }
}
