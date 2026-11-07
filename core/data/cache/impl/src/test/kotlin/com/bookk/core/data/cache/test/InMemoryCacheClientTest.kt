package com.bookk.core.data.cache.test

import com.bookk.core.data.cache.deleteIfEquals
import com.bookk.core.data.cache.get
import com.bookk.core.data.cache.set
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

internal class InMemoryCacheClientTest {

    private class SutFixture {
        val timeSource = TestTimeSource()
        val sut = InMemoryCacheClient(timeSource = timeSource)
        val key = "key"
    }

    @Test
    fun `should keep a key set again until its new longer expiration`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.set(fixture.key, "claim", 1.minutes)
        fixture.sut.set(fixture.key, "processed", 1.hours)

        whenn()
        fixture.timeSource += 2.minutes

        then()
        assertEquals("processed", fixture.sut.get<String, String>(fixture.key))
    }

    @Test
    fun `should expire a key set again at its new shorter expiration`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.set(fixture.key, "processed", 1.hours)
        fixture.sut.set(fixture.key, "claim", 1.minutes)

        whenn()
        fixture.timeSource += 2.minutes

        then()
        assertNull(fixture.sut.get<String, String>(fixture.key))
    }

    @Test
    fun `should keep the expiration of a key that deleteIfEquals does not match`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.set(fixture.key, "claim", 1.minutes)
        fixture.timeSource += 30.seconds
        fixture.sut.deleteIfEquals(fixture.key, "other claim")

        whenn()
        fixture.timeSource += 31.seconds

        then()
        assertNull(fixture.sut.get<String, String>(fixture.key))
    }

    @Test
    fun `should count each increment of a key from one`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val counts = (1..3).map { fixture.sut.increment(fixture.key, 1.minutes) }

        then()
        assertEquals(listOf(1L, 2L, 3L), counts)
    }

    @Test
    fun `should keep the expiration of the first increment when the key is incremented again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.increment(fixture.key, 1.minutes)
        fixture.timeSource += 30.seconds
        fixture.sut.increment(fixture.key, 1.hours)

        whenn()
        fixture.timeSource += 31.seconds
        val count = fixture.sut.increment(fixture.key, 1.minutes)

        then()
        assertEquals(1L, count)
    }

    @Test
    fun `should start a new counter with its own expiration once the previous one expired`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.increment(fixture.key, 1.minutes)
        fixture.timeSource += 2.minutes
        fixture.sut.increment(fixture.key, 10.minutes)

        whenn()
        fixture.timeSource += 5.minutes
        val count = fixture.sut.increment(fixture.key, 1.minutes)

        then()
        assertEquals(2L, count)
    }
}
