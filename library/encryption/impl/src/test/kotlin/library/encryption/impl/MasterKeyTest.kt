package library.encryption.impl

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.crypto.AEADBadTagException
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class MasterKeyTest {

    private class SutFixture {
        val sut = MasterKey.fromBase64(version = 1, encoded = randomBase64Key())
        val subjectId = Uuid.random()
        val subjectKey = randomSecretKey()

        fun stored(wrapped: ByteArray, subjectId: Uuid = this.subjectId, version: Int = sut.version): StoredSubjectKey {
            return StoredSubjectKey(subjectId, wrapped, version, Instant.fromEpochMilliseconds(0))
        }
    }

    @Test
    fun `should unwrap a subject key it wrapped`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val wrapped = fixture.sut.wrap(fixture.subjectId, fixture.subjectKey)

        then()
        assertEquals(StoredSubjectKey.WRAPPED_KEY_LENGTH, wrapped.size)
        val plainKey = fixture.subjectKey.encoded.asList()
        assertFalse(wrapped.asList().windowed(plainKey.size).any { it == plainKey })
        assertArrayEquals(fixture.subjectKey.encoded, fixture.sut.unwrap(fixture.stored(wrapped)).encoded)
    }

    @Test
    fun `should refuse a wrapped key stored for another subject`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val wrapped = fixture.sut.wrap(fixture.subjectId, fixture.subjectKey)

        whenn()
        val result = runCatching { fixture.sut.unwrap(fixture.stored(wrapped, subjectId = Uuid.random())) }

        then()
        assertTrue(result.exceptionOrNull() is AEADBadTagException) { result.toString() }
    }

    @Test
    fun `should refuse a key wrapped by another master key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val other = MasterKey.fromBase64(version = 1, encoded = randomBase64Key())
        val wrapped = other.wrap(fixture.subjectId, fixture.subjectKey)

        whenn()
        val result = runCatching { fixture.sut.unwrap(fixture.stored(wrapped)) }

        then()
        assertTrue(result.exceptionOrNull() is AEADBadTagException) { result.toString() }
    }

    @Test
    fun `should refuse a key wrapped by another master key version`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val wrapped = fixture.sut.wrap(fixture.subjectId, fixture.subjectKey)

        whenn()
        val result = runCatching { fixture.sut.unwrap(fixture.stored(wrapped, version = 2)) }

        then()
        assertTrue(result.exceptionOrNull() is IllegalStateException) { result.toString() }
    }

    @Test
    fun `should refuse a master key that is not 256 bits`() = runUnitTest {
        given()
        val encoded = randomBase64Key(bytes = 16)

        whenn()
        val result = runCatching { MasterKey.fromBase64(version = 1, encoded = encoded) }

        then()
        assertTrue(result.exceptionOrNull() is IllegalArgumentException) { result.toString() }
    }
}
