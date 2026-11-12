package com.bookk.server.appointments.client.api.event

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AppointmentEventTest {

    private val protoBuf = ProtoBuf { encodeDefaults = true }

    private fun cancelledForDeletedClient() = AppointmentEvent.CancelledForDeletedClient(
        employeeUserId = Uuid.random(),
        from = Instant.parse("2099-03-05T14:30:00Z"),
        to = Instant.parse("2099-03-05T15:00:00Z"),
        timeZone = TimeZone.of("Europe/Kyiv"),
        businessName = "Barbershop",
    )

    @Test
    fun `should round trip the cancelled for deleted client event through protobuf`() = runUnitTest {
        given()
        val event = cancelledForDeletedClient()

        whenn()
        val decoded = protoBuf.decodeFromByteArray(
            AppointmentEvent.CancelledForDeletedClient.serializer(),
            protoBuf.encodeToByteArray(AppointmentEvent.CancelledForDeletedClient.serializer(), event),
        )

        then()
        assertEquals(event, decoded)
        assertEquals(AppointmentEvent.CancelledForDeletedClient.TOPIC, decoded.topic)
    }

    @Test
    fun `should decode the cancelled for deleted client event from its pinned field numbers`() = runUnitTest {
        given()
        val event = cancelledForDeletedClient()
        val pinned = PinnedCancelledForDeletedClient(
            employeeUserId = event.employeeUserId,
            from = event.from,
            to = event.to,
            timeZone = event.timeZone,
            businessName = event.businessName,
            idempotencyKey = "key",
            topic = AppointmentEvent.CancelledForDeletedClient.TOPIC,
        )

        whenn()
        val decoded = protoBuf.decodeFromByteArray(
            AppointmentEvent.CancelledForDeletedClient.serializer(),
            protoBuf.encodeToByteArray(PinnedCancelledForDeletedClient.serializer(), pinned),
        )

        then()
        assertEquals(event.copy(idempotencyKey = "key"), decoded)
    }

    @Test
    fun `should decode the client visit booked event from its pinned field numbers`() = runUnitTest {
        given()
        val pinned =
            PinnedClientVisitBooked(
                Uuid.random(),
                Uuid.random(),
                Instant.fromEpochMilliseconds(1_000_000),
                "key",
                AppointmentEvent.ClientVisitBooked.TOPIC,
            )

        whenn()
        val decoded = protoBuf.decodeFromByteArray(
            AppointmentEvent.ClientVisitBooked.serializer(),
            protoBuf.encodeToByteArray(PinnedClientVisitBooked.serializer(), pinned),
        )

        then()
        assertEquals(
            AppointmentEvent.ClientVisitBooked(pinned.businessId, pinned.clientId, pinned.visitEnd, "key"),
            decoded,
        )
        assertEquals(AppointmentEvent.ClientVisitBooked.TOPIC, decoded.topic)
    }

    @Test
    fun `should key the client visit booked event by client id`() = runUnitTest {
        given()
        val event = AppointmentEvent.ClientVisitBooked(
            Uuid.random(),
            Uuid.random(),
            Instant.fromEpochMilliseconds(1_000_000),
        )

        whenn()
        val partitionKey = event.partitionKey

        then()
        assertEquals(event.clientId.toString(), partitionKey)
    }
}

@Serializable
private data class PinnedCancelledForDeletedClient(
    @ProtoNumber(1) val employeeUserId: Uuid,
    @ProtoNumber(2) val from: Instant,
    @ProtoNumber(3) val to: Instant,
    @ProtoNumber(4) val timeZone: TimeZone,
    @ProtoNumber(5) val businessName: String,
    @ProtoNumber(6) val idempotencyKey: String,
    @ProtoNumber(7) val topic: String,
)

@Serializable
private data class PinnedClientVisitBooked(
    @ProtoNumber(1) val businessId: Uuid,
    @ProtoNumber(2) val clientId: Uuid,
    @ProtoNumber(3) val visitEnd: Instant,
    @ProtoNumber(4) val idempotencyKey: String,
    @ProtoNumber(5) val topic: String,
)
