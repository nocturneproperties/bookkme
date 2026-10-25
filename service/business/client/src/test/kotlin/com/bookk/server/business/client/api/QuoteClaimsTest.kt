package com.bookk.server.business.client.api

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class QuoteClaimsTest {

    @Test
    fun `should encode each distinct service id once with its requested count`() = runUnitTest {
        given()
        val serviceX = Uuid.random()
        val serviceY = Uuid.random()
        val serviceIds = listOf(serviceX, serviceX, serviceX, serviceX, serviceX, serviceY)

        whenn()
        val encoded = QuoteClaims.encodeServiceCounts(serviceIds)

        then()
        assertEquals(2, encoded.size)
        assertEquals(mapOf(serviceX to 5, serviceY to 1), QuoteClaims.decodeServiceCounts(encoded))
    }

    @Test
    fun `should round trip counts through encode and decode`() = runUnitTest {
        given()
        val serviceIds = listOf(Uuid.random(), Uuid.random(), Uuid.random())

        whenn()
        val decoded = QuoteClaims.decodeServiceCounts(QuoteClaims.encodeServiceCounts(serviceIds))

        then()
        assertEquals(serviceIds.groupingBy { it }.eachCount(), decoded)
    }

    @Test
    fun `should ignore malformed claim entries when decoding`() = runUnitTest {
        given()
        val malformedClaim = listOf(Uuid.random().toString(), "not-a-uuid:5", "${Uuid.random()}:not-a-number")

        whenn()
        val decoded = QuoteClaims.decodeServiceCounts(malformedClaim)

        then()
        assertEquals(emptyMap<Uuid, Int>(), decoded)
    }
}
