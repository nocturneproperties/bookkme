package com.bookk.server.user.client.api.event

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class UserEventTest {

    private val userId = Uuid.random()

    private fun updated(id: Uuid = userId, name: String = "Name") = UserEvent.Updated(
        userId = id,
        name = name,
        lastName = "Last name",
        email = "user@example.com",
        phone = null,
        updatedAt = Instant.fromEpochMilliseconds(1000)
    )

    @Test
    fun `should key the updated event by user id`() = runUnitTest {
        given()
        val event = updated()

        whenn()
        val partitionKey = event.partitionKey

        then()
        assertEquals(userId.toString(), partitionKey)
    }

    @Test
    fun `should route consecutive updates of one user to the same partition key`() = runUnitTest {
        given()
        val first = updated(name = "First")
        val second = updated(name = "Second")

        whenn()
        val keys = setOf(first.partitionKey, second.partitionKey)

        then()
        assertEquals(1, keys.size)
    }

    @Test
    fun `should route updates of different users to different partition keys`() = runUnitTest {
        given()
        val mine = updated()
        val other = updated(id = Uuid.random())

        whenn()
        val keys = setOf(mine.partitionKey, other.partitionKey)

        then()
        assertEquals(2, keys.size)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `should keep the partition key out of the serialized form`() = runUnitTest {
        given()
        val descriptor = UserEvent.Updated.serializer().descriptor

        whenn()
        val serializedFields = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }

        then()
        assertFalse(serializedFields.contains("partitionKey"))
    }
}
