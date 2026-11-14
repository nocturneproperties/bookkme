package library.encryption

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.encryption.test.InMemorySubjectKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class TextEncryptionTest {

    private class SutFixture {
        val subjectKeys = InMemorySubjectKeys()
        val subjectId = Uuid.random()

        suspend fun encrypt(text: String): ByteArray {
            return subjectKeys.encryptor(subjectId)!!.encryptText(text)
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
}
