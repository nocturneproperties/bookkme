package library.encryption

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.encryption.test.InMemorySubjectKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class TextEncryptionTest {

    private class SutFixture {
        val subjectKeys = InMemorySubjectKeys()
        val subjectId = Uuid.random()

        suspend fun encryptor(): Encryptor {
            return subjectKeys.encryptor(subjectId)!!
        }

        suspend fun encrypt(text: String): ByteArray {
            return encryptor().encryptText(text)
        }

        suspend fun decrypt(ciphertext: ByteArray): Decrypted<String> {
            return subjectKeys.decryptor(listOf(subjectId)).decryptText(subjectId, ciphertext)
        }
    }

    @Test
    fun `should read back the text it encrypted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ciphertext = fixture.encrypt("Alice")

        whenn()
        val decrypted = fixture.decrypt(ciphertext)

        then()
        assertEquals("Alice", (decrypted as Decrypted.Plain).value)
    }

    @Test
    fun `should not store the text readable`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val ciphertext = fixture.encrypt("Alice")

        then()
        assertFalse(ciphertext.contentEquals("Alice".encodeToByteArray()))
    }

    @Test
    fun `should report a shredded subject's text as shredded`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ciphertext = fixture.encrypt("Alice")
        fixture.subjectKeys.shred(fixture.subjectId)

        whenn()
        val decrypted = fixture.decrypt(ciphertext)

        then()
        assertSame(Decrypted.Shredded, decrypted)
    }

    @Test
    fun `should report the text of a subject without a key as missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ciphertext = fixture.encrypt("Alice")
        fixture.subjectKeys.loseKey(fixture.subjectId)

        whenn()
        val decrypted = fixture.decrypt(ciphertext)

        then()
        assertSame(Decrypted.Missing, decrypted)
    }

    @Test
    fun `should report text sealed under a key that was lost and replaced as missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ciphertext = fixture.encrypt("Alice")
        fixture.subjectKeys.loseKey(fixture.subjectId)
        val reentered = fixture.encrypt("Alicia")

        whenn()
        val decrypted = fixture.decrypt(ciphertext)

        then()
        assertSame(Decrypted.Missing, decrypted)
        assertEquals("Alicia", fixture.decrypt(reentered).valueOrNull())
    }

    @Test
    fun `should report text that fails authentication as missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ciphertext = fixture.encrypt("Alice")
        fixture.subjectKeys.tamperWithCiphertextsOf(fixture.subjectId)

        whenn()
        val decrypted = fixture.decrypt(ciphertext)

        then()
        assertSame(Decrypted.Missing, decrypted)
    }

    @Test
    fun `should refuse to decrypt a subject that was not requested`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ciphertext = fixture.encrypt("Alice")

        whenn()
        val result = runCatching {
            fixture.subjectKeys.decryptor(listOf(Uuid.random())).decryptText(fixture.subjectId, ciphertext)
        }

        then()
        assertTrue(result.exceptionOrNull() is IllegalArgumentException) { result.toString() }
    }

    @Test
    fun `should give the value of a plain result`() = runUnitTest {
        given()
        val decrypted: Decrypted<String> = Decrypted.Plain("Alice")

        whenn()
        val value = decrypted.valueOrNull()

        then()
        assertEquals("Alice", value)
    }

    @Test
    fun `should give no value for a shredded or missing result`() = runUnitTest {
        given()
        val shredded: Decrypted<String> = Decrypted.Shredded
        val missing: Decrypted<String> = Decrypted.Missing

        whenn()
        val values = listOf(shredded.valueOrNull(), missing.valueOrNull())

        then()
        assertEquals(listOf(null, null), values)
    }

    @Test
    fun `should transform the value of a plain result`() = runUnitTest {
        given()
        val decrypted: Decrypted<String> = Decrypted.Plain("Alice")

        whenn()
        val mapped = decrypted.map { it.length }

        then()
        assertEquals(5, (mapped as Decrypted.Plain).value)
    }

    @Test
    fun `should keep a shredded or missing result when transforming it`() = runUnitTest {
        given()
        val shredded: Decrypted<String> = Decrypted.Shredded
        val missing: Decrypted<String> = Decrypted.Missing
        var transformed = false

        whenn()
        val mapped = listOf(shredded, missing).map { result -> result.map { transformed = true } }

        then()
        assertEquals(listOf(Decrypted.Shredded, Decrypted.Missing), mapped)
        assertFalse(transformed)
    }

    @Test
    fun `should keep the stored ciphertext of text that did not change`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val stored = fixture.encrypt("Alice")

        whenn()
        val ciphertext = fixture.encryptor().encryptTextIfChanged(stored, current = "Alice", edited = "Alice")

        then()
        assertSame(stored, ciphertext)
    }

    @Test
    fun `should encrypt text that changed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val stored = fixture.encrypt("Alice")

        whenn()
        val ciphertext = fixture.encryptor().encryptTextIfChanged(stored, current = "Alice", edited = "Alicia")

        then()
        assertEquals("Alicia", fixture.decrypt(ciphertext).valueOrNull())
    }

    @Test
    fun `should keep the stored ciphertext of optional text that did not change`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val stored = fixture.encrypt("alice@example.com")
        val encryptor = fixture.encryptor()

        whenn()
        val kept = encryptor.encryptOptionalTextIfChanged(stored, "alice@example.com", "alice@example.com")
        val stillAbsent = encryptor.encryptOptionalTextIfChanged(stored = null, current = null, edited = null)

        then()
        assertSame(stored, kept)
        assertNull(stillAbsent)
    }

    @Test
    fun `should encrypt optional text that was added or changed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val stored = fixture.encrypt("alice@example.com")
        val encryptor = fixture.encryptor()

        whenn()
        val added = encryptor.encryptOptionalTextIfChanged(stored = null, current = null, edited = "alice@example.com")
        val changed = encryptor.encryptOptionalTextIfChanged(stored, "alice@example.com", "alicia@example.com")

        then()
        assertEquals("alice@example.com", fixture.decrypt(added!!).valueOrNull())
        assertEquals("alicia@example.com", fixture.decrypt(changed!!).valueOrNull())
    }

    @Test
    fun `should drop the ciphertext of optional text that was cleared`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val stored = fixture.encrypt("alice@example.com")

        whenn()
        val ciphertext = fixture.encryptor().encryptOptionalTextIfChanged(stored, "alice@example.com", edited = null)

        then()
        assertNull(ciphertext)
    }
}
