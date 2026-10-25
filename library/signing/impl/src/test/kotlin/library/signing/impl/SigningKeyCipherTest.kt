@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package library.signing.impl

import library.signing.impl.key.SigningKeyCipher
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

internal class SigningKeyCipherTest {

    private fun randomKey(): String =
        Base64.encode(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().encoded)

    @Test
    fun `should decrypt back to the original plaintext`() {
        val key = randomKey()
        val plaintext = "-----BEGIN PRIVATE KEY-----\nsome-pem-content\n-----END PRIVATE KEY-----"

        val ciphertext = SigningKeyCipher.encrypt(plaintext, key)

        assertNotEquals(plaintext, ciphertext)
        assertEquals(plaintext, SigningKeyCipher.decrypt(ciphertext, key))
    }

    @Test
    fun `should produce different ciphertext for the same plaintext on each call`() {
        val key = randomKey()
        val plaintext = "same-private-key-pem"

        val first = SigningKeyCipher.encrypt(plaintext, key)
        val second = SigningKeyCipher.encrypt(plaintext, key)

        assertNotEquals(first, second)
    }

    @Test
    fun `should fail to decrypt with the wrong key`() {
        val plaintext = "private-key-pem"
        val ciphertext = SigningKeyCipher.encrypt(plaintext, randomKey())

        assertFailsWith<AEADBadTagException> { SigningKeyCipher.decrypt(ciphertext, randomKey()) }
    }
}
