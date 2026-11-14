package com.bookk.server.business.client.api

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import library.schedule.Schedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

@Serializable
private data class PinnedBusinessBeforeOwner(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val address: String,
    @ProtoNumber(4) val timeZone: TimeZone,
    @ProtoNumber(5) val schedule: Schedule,
)

internal class BusinessDTOTest {

    private val protoBuf = ProtoBuf { encodeDefaults = true }

    @Test
    fun `should carry the business owner`() = runUnitTest {
        given()
        val ownerId = Uuid.random()
        val business = Business.stub(ownerId = ownerId)

        whenn()
        val dto = BusinessDTO.from(business)

        then()
        assertEquals(ownerId, dto.ownerUserId)
    }

    @Test
    fun `should round trip the business owner`() = runUnitTest {
        given()
        val dto = BusinessDTO.from(Business.stub())

        whenn()
        val decoded = protoBuf.decodeFromByteArray(
            BusinessDTO.serializer(),
            protoBuf.encodeToByteArray(BusinessDTO.serializer(), dto),
        )

        then()
        assertEquals(dto, decoded)
    }

    @Test
    fun `should decode a business sent before the owner field without an owner`() = runUnitTest {
        given()
        val pinned = PinnedBusinessBeforeOwner(Uuid.random(), "Name", "Address", TimeZone.UTC, Schedule())

        whenn()
        val decoded = protoBuf.decodeFromByteArray(
            BusinessDTO.serializer(),
            protoBuf.encodeToByteArray(PinnedBusinessBeforeOwner.serializer(), pinned),
        )

        then()
        assertNull(decoded.ownerUserId)
        assertEquals(pinned.id, decoded.id)
    }
}
