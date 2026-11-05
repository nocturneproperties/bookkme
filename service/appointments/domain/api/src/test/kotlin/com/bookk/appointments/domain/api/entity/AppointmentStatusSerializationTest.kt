package com.bookk.appointments.domain.api.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AppointmentStatusSerializationTest {
    @Serializable
    private data class StatusMessage(@ProtoNumber(1) val status: AppointmentStatus)

    @Test
    fun `should preserve existing status wire numbers and append unresolved`() = runUnitTest {
        given()
        val statuses = listOf(
            AppointmentStatus.SCHEDULED,
            AppointmentStatus.COMPLETED,
            AppointmentStatus.CANCELLED,
            AppointmentStatus.NO_SHOW,
            AppointmentStatus.UNRESOLVED,
        )

        whenn()
        val encoded = statuses.map { ProtoBuf.encodeToByteArray(StatusMessage.serializer(), StatusMessage(it)) }

        then()
        encoded.forEachIndexed { number, bytes ->
            assertArrayEquals(byteArrayOf(8, number.toByte()), bytes)
            assertEquals(statuses[number], ProtoBuf.decodeFromByteArray(StatusMessage.serializer(), bytes).status)
        }
    }
}
