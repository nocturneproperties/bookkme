package com.bookk.server.business.client.api.event

import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.business.client.api.BusinessDTO
import kotlinx.datetime.TimeZone
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import library.schedule.Schedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
private data class LegacyEmployeePermissionsChanged(
    val employeeUserId: Uuid,
    val businessId: Uuid,
    val permissions: BusinessPermissions,
    val idempotencyKey: String
) {
    val topic: String = BusinessEvent.EmployeePermissionsChanged.TOPIC
}

internal class BusinessEventTest {

    private val protoBuf = ProtoBuf { encodeDefaults = true }

    private val businessId = Uuid.random()

    private fun businessDto(id: Uuid = businessId) = BusinessDTO(
        id = id,
        name = "Business name",
        address = "Business address",
        timeZone = TimeZone.UTC,
        schedule = Schedule()
    )

    @Test
    fun `should key the updated event by business id`() = runUnitTest {
        given()
        val event = BusinessEvent.Updated(businessDto(), Instant.fromEpochMilliseconds(1000))

        whenn()
        val partitionKey = event.partitionKey

        then()
        assertEquals(businessId.toString(), partitionKey)
    }

    @Test
    fun `should key the deleted event by business id`() = runUnitTest {
        given()
        val event = BusinessEvent.Deleted(businessId)

        whenn()
        val partitionKey = event.partitionKey

        then()
        assertEquals(businessId.toString(), partitionKey)
    }

    @Test
    fun `should route consecutive updates of one business to the same partition key`() = runUnitTest {
        given()
        val first = BusinessEvent.Updated(businessDto(), Instant.fromEpochMilliseconds(1000))
        val second = BusinessEvent.Updated(businessDto(), Instant.fromEpochMilliseconds(2000))

        whenn()
        val keys = setOf(first.partitionKey, second.partitionKey)

        then()
        assertEquals(1, keys.size)
    }

    @Test
    fun `should route updates of different businesses to different partition keys`() = runUnitTest {
        given()
        val mine = BusinessEvent.Updated(businessDto(), Instant.fromEpochMilliseconds(1000))
        val other = BusinessEvent.Updated(businessDto(id = Uuid.random()), Instant.fromEpochMilliseconds(1000))

        whenn()
        val keys = setOf(mine.partitionKey, other.partitionKey)

        then()
        assertEquals(2, keys.size)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `should keep the partition key out of the serialized form`() = runUnitTest {
        given()
        val descriptor = BusinessEvent.Updated.serializer().descriptor

        whenn()
        val serializedFields = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }

        then()
        assertFalse(serializedFields.contains("partitionKey"))
        assertEquals(listOf("business", "updatedAt", "idempotencyKey", "topic"), serializedFields)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `should decode an employee permissions changed event produced before the suspended flag existed as not suspended`() = runUnitTest {
        given()
        val legacy = LegacyEmployeePermissionsChanged(Uuid.random(), businessId, BusinessPermissions.VIEW_ONLY, "key")
        val bytes = protoBuf.encodeToByteArray(LegacyEmployeePermissionsChanged.serializer(), legacy)

        whenn()
        val decoded = protoBuf.decodeFromByteArray(BusinessEvent.EmployeePermissionsChanged.serializer(), bytes)

        then()
        assertEquals(
            BusinessEvent.EmployeePermissionsChanged(legacy.employeeUserId, businessId, BusinessPermissions.VIEW_ONLY, "key", suspended = false),
            decoded
        )
        assertEquals(BusinessEvent.EmployeePermissionsChanged.TOPIC, decoded.topic)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `should let a consumer without the suspended flag decode a new employee permissions changed event`() = runUnitTest {
        given()
        val event = BusinessEvent.EmployeePermissionsChanged(Uuid.random(), businessId, BusinessPermissions.NONE, "key", suspended = true)
        val bytes = protoBuf.encodeToByteArray(BusinessEvent.EmployeePermissionsChanged.serializer(), event)

        whenn()
        val decoded = protoBuf.decodeFromByteArray(LegacyEmployeePermissionsChanged.serializer(), bytes)

        then()
        assertEquals(LegacyEmployeePermissionsChanged(event.employeeUserId, businessId, BusinessPermissions.NONE, "key"), decoded)
        assertEquals(BusinessEvent.EmployeePermissionsChanged.TOPIC, decoded.topic)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `should round trip the suspended flag`() = runUnitTest {
        given()
        val event = BusinessEvent.EmployeePermissionsChanged(Uuid.random(), businessId, BusinessPermissions.NONE, suspended = true)

        whenn()
        val decoded = protoBuf.decodeFromByteArray(
            BusinessEvent.EmployeePermissionsChanged.serializer(),
            protoBuf.encodeToByteArray(BusinessEvent.EmployeePermissionsChanged.serializer(), event)
        )

        then()
        assertEquals(event, decoded)
    }
}
