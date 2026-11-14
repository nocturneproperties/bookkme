package library.encryption.impl

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.encryption.ofCaseInsensitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class HmacBlindIndexTest {

    private class SutFixture(key: String = randomBase64Key()) {
        val sut = HmacBlindIndex.fromBase64(key)
    }

    @Test
    fun `should index the same value the same way`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val first = fixture.sut.of(VALUE)
        val second = fixture.sut.of(VALUE)

        then()
        assertEquals(first, second)
        assertEquals(HmacBlindIndex.LENGTH, first.length)
    }

    @Test
    fun `should index different values differently`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val first = fixture.sut.of(VALUE)
        val second = fixture.sut.of("john.doe@example.com")

        then()
        assertNotEquals(first, second)
    }

    @Test
    fun `should index a value differently under another key`() = runUnitTest {
        given()
        val first = SutFixture()
        val second = SutFixture()

        whenn()
        val firstIndex = first.sut.of(VALUE)
        val secondIndex = second.sut.of(VALUE)

        then()
        assertNotEquals(firstIndex, secondIndex)
    }

    @Test
    fun `should index a contact the same way regardless of its case and surrounding spaces`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val typed = fixture.sut.ofCaseInsensitive("  Jane.Doe@Example.COM ")
        val stored = fixture.sut.ofCaseInsensitive(VALUE)

        then()
        assertEquals(stored, typed)
        assertEquals(fixture.sut.of(VALUE), stored)
    }

    @Test
    fun `should refuse a key shorter than 256 bits`() = runUnitTest {
        given()
        val key = randomBase64Key(bytes = 16)

        whenn()
        val result = runCatching { HmacBlindIndex.fromBase64(key) }

        then()
        assertTrue(result.exceptionOrNull() is IllegalArgumentException) { result.toString() }
    }

    private companion object {
        const val VALUE = "jane.doe@example.com"
    }
}
