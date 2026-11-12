package library.encryption.impl

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.crypto.AEADBadTagException
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

internal class FieldCipherTest {

    private class SutFixture {
        val key = randomSecretKey()
        val subjectId = Uuid.random()
    }

    @Test
    fun `should decrypt a field back to its plaintext`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val ciphertext = FieldCipher.encrypt(fixture.key, fixture.subjectId, PLAINTEXT)

        then()
        assertFalse(ciphertext.contains(PLAINTEXT))
        assertEquals(PLAINTEXT, FieldCipher.decrypt(fixture.key, fixture.subjectId, ciphertext))
    }

    @Test
    fun `should encrypt the same plaintext differently each time`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val first = FieldCipher.encrypt(fixture.key, fixture.subjectId, PLAINTEXT)
        val second = FieldCipher.encrypt(fixture.key, fixture.subjectId, PLAINTEXT)

        then()
        assertNotEquals(first, second)
    }

    @Test
    fun `should refuse to decrypt with another key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ciphertext = FieldCipher.encrypt(fixture.key, fixture.subjectId, PLAINTEXT)

        whenn()
        val result = runCatching { FieldCipher.decrypt(randomSecretKey(), fixture.subjectId, ciphertext) }

        then()
        assertTrue(result.exceptionOrNull() is AEADBadTagException) { result.toString() }
    }

    @Test
    fun `should refuse a field copied onto another subject`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ciphertext = FieldCipher.encrypt(fixture.key, fixture.subjectId, PLAINTEXT)

        whenn()
        val result = runCatching { FieldCipher.decrypt(fixture.key, Uuid.random(), ciphertext) }

        then()
        assertTrue(result.exceptionOrNull() is AEADBadTagException) { result.toString() }
    }

    @Test
    fun `should refuse a tampered field`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val bytes = Base64.decode(FieldCipher.encrypt(fixture.key, fixture.subjectId, PLAINTEXT))
        bytes[bytes.lastIndex] = (bytes.last() + 1).toByte()

        whenn()
        val result = runCatching { FieldCipher.decrypt(fixture.key, fixture.subjectId, Base64.encode(bytes)) }

        then()
        assertTrue(result.exceptionOrNull() is AEADBadTagException) { result.toString() }
    }

    @Test
    fun `should refuse a field in an unknown format`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val bytes = Base64.decode(FieldCipher.encrypt(fixture.key, fixture.subjectId, PLAINTEXT))
        bytes[0] = 99

        whenn()
        val result = runCatching { FieldCipher.decrypt(fixture.key, fixture.subjectId, Base64.encode(bytes)) }

        then()
        assertTrue(result.exceptionOrNull() is IllegalArgumentException) { result.toString() }
    }

    @Test
    fun `should keep non-ASCII text intact`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val plaintext = "Олена Ковальчук, ул. Шевченка 1 🙂"

        whenn()
        val ciphertext = FieldCipher.encrypt(fixture.key, fixture.subjectId, plaintext)

        then()
        assertEquals(plaintext, FieldCipher.decrypt(fixture.key, fixture.subjectId, ciphertext))
    }

    private companion object {
        const val PLAINTEXT = "jane.doe@example.com"
    }
}
