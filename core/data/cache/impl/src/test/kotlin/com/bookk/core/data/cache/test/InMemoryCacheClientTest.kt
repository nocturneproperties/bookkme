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
}
