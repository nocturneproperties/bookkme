package com.bookk.server.auth.client

import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class AuthEventTest {

    private val deviceUuid = Uuid.random()

    private fun languageUpdated(device: Uuid = deviceUuid, language: Language = Language.EN) =
        AuthEvent.DeviceLanguageUpdated(deviceUuid = device, language = language)

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

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `should keep the partition key out of the serialized form`() = runUnitTest {
        given()
        val descriptor = AuthEvent.DeviceLanguageUpdated.serializer().descriptor

        whenn()
        val serializedFields = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }

        then()
        assertFalse(serializedFields.contains("partitionKey"))
    }
}
