package com.bookk.server.appointments.client.api.event

import com.bookk.core.data.eventstreaming.PersonalEvent
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `should decode a cancelled for deleted client event written with the removed cause`() = runUnitTest {
        given()
        val event = cancelledForDeletedClient()
        val pinned = PinnedCancelledForDeletedClientBefore021(
            employeeUserId = event.employeeUserId,
            from = event.from,
            to = event.to,
            timeZone = event.timeZone,
            businessName = event.businessName,
            idempotencyKey = "key",
            topic = AppointmentEvent.CancelledForDeletedClient.TOPIC,
            cause = 2,
        )

        whenn()
        val decoded = protoBuf.decodeFromByteArray(
            AppointmentEvent.CancelledForDeletedClient.serializer(),
            protoBuf.encodeToByteArray(PinnedCancelledForDeletedClientBefore021.serializer(), pinned),
        )

        then()
        assertEquals(event.copy(idempotencyKey = "key"), decoded)
    }

    @Test
    fun `should carry the requested service names in the request created event`() = runUnitTest {
        given()
        val event = AppointmentEvent.RequestCreated(
            clientUserId = Uuid.random(),
            employeeUserId = Uuid.random(),
            from = Instant.parse("2099-03-05T14:30:00Z"),
            to = Instant.parse("2099-03-05T15:00:00Z"),
            timeZone = TimeZone.of("Europe/Kyiv"),
            price = "100 UAH",
            requestId = null,
            serviceNames = listOf("Haircut", "Beard trim", "Beard trim"),
        )

        whenn()
        val decoded = protoBuf.decodeFromByteArray(
            AppointmentEvent.RequestCreated.serializer(),
            protoBuf.encodeToByteArray(AppointmentEvent.RequestCreated.serializer(), event),
        )

        then()
        assertEquals(event, decoded)
    }

    @Test
    fun `should decode a cancelled event written before the reason kind was added as a stated reason`() = runUnitTest {
        given()
        val pinned = PinnedCancelledBefore021(
            clientUserId = Uuid.random(),
            employeeUserId = Uuid.random(),
            from = Instant.parse("2099-03-05T14:30:00Z"),
            to = Instant.parse("2099-03-05T15:00:00Z"),
            timeZone = TimeZone.of("Europe/Kyiv"),
            address = "Main street 1",
            businessName = "Barbershop",
            price = "100 UAH",
            reason = "Staff unavailable",
            idempotencyKey = "key",
            topic = AppointmentEvent.Cancelled.TOPIC,
        )

        whenn()
        val decoded = protoBuf.decodeFromByteArray(
            AppointmentEvent.Cancelled.serializer(),
            protoBuf.encodeToByteArray(PinnedCancelledBefore021.serializer(), pinned),
        )

        then()
        assertEquals(CancellationReasonKind.STATED, decoded.reasonKind)
        assertEquals("Staff unavailable", decoded.reason)
    }

    @Test
    fun `should round trip the reason kind of a cancelled event`() = runUnitTest {
        given()
        val event = AppointmentEvent.Cancelled(
            clientUserId = Uuid.random(),
            employeeUserId = Uuid.random(),
            from = Instant.parse("2099-03-05T14:30:00Z"),
            to = Instant.parse("2099-03-05T15:00:00Z"),
            timeZone = TimeZone.of("Europe/Kyiv"),
            businessName = "Barbershop",
            price = "100 UAH",
            reason = "Client was removed by the business",
            reasonKind = CancellationReasonKind.CLIENT_REMOVED,
        )

        whenn()
        val decoded = protoBuf.decodeFromByteArray(
            AppointmentEvent.Cancelled.serializer(),
            protoBuf.encodeToByteArray(AppointmentEvent.Cancelled.serializer(), event),
        )

        then()
        assertEquals(event, decoded)
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

    @Test
    fun `should decode a request created event written before its unused fields were dropped`() = runUnitTest {
        given()
        val pinned = PinnedRequestEventBefore018(
            clientUserId = Uuid.random(),
            clientName = "Alice",
            employeeUserId = Uuid.random(),
            employeeName = "Bob",
            from = Instant.parse("2099-03-05T14:30:00Z"),
            to = Instant.parse("2099-03-05T15:00:00Z"),
            timeZone = TimeZone.of("Europe/Kyiv"),
            businessName = "Barbershop",
            address = "Main street 1",
            price = "100 UAH",
            idempotencyKey = "key",
            topic = AppointmentEvent.RequestCreated.TOPIC,
        )

        whenn()
        val decoded = protoBuf.decodeFromByteArray(
            AppointmentEvent.RequestCreated.serializer(),
            protoBuf.encodeToByteArray(PinnedRequestEventBefore018.serializer(), pinned),
        )

        then()
        val expected = AppointmentEvent.RequestCreated(
            clientUserId = requireNotNull(pinned.clientUserId),
            employeeUserId = pinned.employeeUserId,
            from = pinned.from,
            to = pinned.to,
            timeZone = pinned.timeZone,
            price = pinned.price,
            requestId = null,
            idempotencyKey = "key",
        )
        assertEquals(expected, decoded)
    }

    @Test
    fun `should carry the request id in the request created event`() = runUnitTest {
        given()
        val event = AppointmentEvent.RequestCreated(
            clientUserId = Uuid.random(),
            employeeUserId = Uuid.random(),
            from = Instant.parse("2099-03-05T14:30:00Z"),
            to = Instant.parse("2099-03-05T15:00:00Z"),
            timeZone = TimeZone.of("Europe/Kyiv"),
            price = "100 UAH",
            requestId = Uuid.random(),
        )

        whenn()
        val decoded = protoBuf.decodeFromByteArray(
            AppointmentEvent.RequestCreated.serializer(),
            protoBuf.encodeToByteArray(AppointmentEvent.RequestCreated.serializer(), event),
        )

        then()
        assertEquals(event, decoded)
    }

    @Test
    fun `should decode a request approved event written before the names were dropped`() = runUnitTest {
        given()
        val pinned = PinnedRequestEventBefore018(
            clientUserId = Uuid.random(),
            clientName = "Alice",
            employeeUserId = Uuid.random(),
            employeeName = "Bob",
            from = Instant.parse("2099-03-05T14:30:00Z"),
            to = Instant.parse("2099-03-05T15:00:00Z"),
            timeZone = TimeZone.of("Europe/Kyiv"),
            businessName = "Barbershop",
            address = "Main street 1",
            price = "100 UAH",
            idempotencyKey = "key",
            topic = AppointmentEvent.RequestApproved.TOPIC,
        )

        whenn()
        val decoded = protoBuf.decodeFromByteArray(
            AppointmentEvent.RequestApproved.serializer(),
            protoBuf.encodeToByteArray(PinnedRequestEventBefore018.serializer(), pinned),
        )

        then()
        val expected = AppointmentEvent.RequestApproved(
            clientUserId = requireNotNull(pinned.clientUserId),
            employeeUserId = pinned.employeeUserId,
            from = pinned.from,
            to = pinned.to,
            timeZone = pinned.timeZone,
            businessName = pinned.businessName,
            address = pinned.address,
            price = pinned.price,
            idempotencyKey = "key",
        )
        assertEquals(expected, decoded)
        assertEquals(AppointmentEvent.RequestApproved.TOPIC, decoded.topic)
    }

    @Test
    fun `should decode a request rejected event written before the names were dropped`() = runUnitTest {
        given()
        val pinned = PinnedRequestRejectedBefore018(
            clientUserId = Uuid.random(),
            clientName = "Alice",
            employeeUserId = Uuid.random(),
            employeeName = "Bob",
            from = Instant.parse("2099-03-05T14:30:00Z"),
            to = Instant.parse("2099-03-05T15:00:00Z"),
            timeZone = TimeZone.of("Europe/Kyiv"),
            address = "Main street 1",
            businessName = "Barbershop",
            price = "100 UAH",
            declineReason = "Fully booked",
            idempotencyKey = "key",
            topic = AppointmentEvent.RequestRejected.TOPIC,
        )

        whenn()
        val decoded = protoBuf.decodeFromByteArray(
            AppointmentEvent.RequestRejected.serializer(),
            protoBuf.encodeToByteArray(PinnedRequestRejectedBefore018.serializer(), pinned),
        )

        then()
        val expected = AppointmentEvent.RequestRejected(
            clientUserId = requireNotNull(pinned.clientUserId),
            employeeUserId = pinned.employeeUserId,
            from = pinned.from,
            to = pinned.to,
            timeZone = pinned.timeZone,
            businessName = pinned.businessName,
            price = pinned.price,
            declineReason = pinned.declineReason,
            idempotencyKey = "key",
        )
        assertEquals(expected, decoded)
        assertEquals(AppointmentEvent.RequestRejected.TOPIC, decoded.topic)
    }

    @Test
    fun `should not treat a new request as personal data`() = runUnitTest {
        given()
        val event: AppointmentEvent = AppointmentEvent.RequestCreated(
            clientUserId = Uuid.random(),
            employeeUserId = Uuid.random(),
            from = Instant.parse("2099-03-05T14:30:00Z"),
            to = Instant.parse("2099-03-05T15:00:00Z"),
            timeZone = TimeZone.of("Europe/Kyiv"),
            price = "100 UAH",
            requestId = Uuid.random(),
        )

        whenn()
        val personal = event is PersonalEvent

        then()
        assertFalse(personal)
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
private data class PinnedCancelledBefore021(
    @ProtoNumber(1) val clientUserId: Uuid?,
    @ProtoNumber(3) val employeeUserId: Uuid,
    @ProtoNumber(5) val from: Instant,
    @ProtoNumber(6) val to: Instant,
    @ProtoNumber(7) val timeZone: TimeZone,
    @ProtoNumber(8) val address: String,
    @ProtoNumber(9) val businessName: String,
    @ProtoNumber(10) val price: String,
    @ProtoNumber(11) val reason: String,
    @ProtoNumber(12) val idempotencyKey: String,
    @ProtoNumber(13) val topic: String,
)

@Serializable
private data class PinnedCancelledForDeletedClientBefore021(
    @ProtoNumber(1) val employeeUserId: Uuid,
    @ProtoNumber(2) val from: Instant,
    @ProtoNumber(3) val to: Instant,
    @ProtoNumber(4) val timeZone: TimeZone,
    @ProtoNumber(5) val businessName: String,
    @ProtoNumber(6) val idempotencyKey: String,
    @ProtoNumber(7) val topic: String,
    @ProtoNumber(8) val cause: Int,
)

@Serializable
private data class PinnedRequestEventBefore018(
    @ProtoNumber(1) val clientUserId: Uuid?,
    @ProtoNumber(2) val clientName: String,
    @ProtoNumber(3) val employeeUserId: Uuid,
    @ProtoNumber(4) val employeeName: String,
    @ProtoNumber(5) val from: Instant,
    @ProtoNumber(6) val to: Instant,
    @ProtoNumber(7) val timeZone: TimeZone,
    @ProtoNumber(8) val businessName: String,
    @ProtoNumber(9) val address: String,
    @ProtoNumber(10) val price: String,
    @ProtoNumber(11) val idempotencyKey: String,
    @ProtoNumber(12) val topic: String,
)

@Serializable
private data class PinnedRequestRejectedBefore018(
    @ProtoNumber(1) val clientUserId: Uuid?,
    @ProtoNumber(2) val clientName: String,
    @ProtoNumber(3) val employeeUserId: Uuid,
    @ProtoNumber(4) val employeeName: String,
    @ProtoNumber(5) val from: Instant,
    @ProtoNumber(6) val to: Instant,
    @ProtoNumber(7) val timeZone: TimeZone,
    @ProtoNumber(8) val address: String,
    @ProtoNumber(9) val businessName: String,
    @ProtoNumber(10) val price: String,
    @ProtoNumber(11) val declineReason: String,
    @ProtoNumber(12) val idempotencyKey: String,
    @ProtoNumber(13) val topic: String,
)

@Serializable
private data class PinnedClientVisitBooked(
    @ProtoNumber(1) val businessId: Uuid,
    @ProtoNumber(2) val clientId: Uuid,
    @ProtoNumber(3) val visitEnd: Instant,
    @ProtoNumber(4) val idempotencyKey: String,
    @ProtoNumber(5) val topic: String,
)
