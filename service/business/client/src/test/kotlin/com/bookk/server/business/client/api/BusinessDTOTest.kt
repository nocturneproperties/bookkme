package com.bookk.server.business.client.api

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import library.schedule.Schedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `should refuse a business sent without its owner`() = runUnitTest {
        given()
        val pinned = PinnedBusinessBeforeOwner(Uuid.random(), "Name", "Address", TimeZone.UTC, Schedule())
        val bytes = protoBuf.encodeToByteArray(PinnedBusinessBeforeOwner.serializer(), pinned)

        whenn()
        val result = runCatching { protoBuf.decodeFromByteArray(BusinessDTO.serializer(), bytes) }

        then()
        assertTrue(result.exceptionOrNull() is SerializationException)
    }
}
