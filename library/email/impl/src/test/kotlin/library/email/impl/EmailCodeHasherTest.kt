package library.email.impl

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import kotlin.uuid.Uuid

internal class EmailCodeHasherTest {

    private class SutFixture(secret: String = SECRET) {
        val sut = EmailCodeHasher.fromSecret(secret)
    }

    @Test
    fun `should hash the same code for different verifications differently`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = Uuid.random()
        val second = Uuid.random()

        whenn()
        val firstHash = fixture.sut.codeHash(first, CODE)
        val secondHash = fixture.sut.codeHash(second, CODE)

        then()
        assertNotEquals(firstHash, secondHash)
        assertEquals(firstHash, fixture.sut.codeHash(first, CODE))
    }

    @Test
    fun `should hash a code differently under another secret`() = runUnitTest {
        given()
        val verificationId = Uuid.random()
        val other = SutFixture(SECRET.reversed())

        whenn()
        val hash = SutFixture().sut.codeHash(verificationId, CODE)

        then()
        assertNotEquals(other.sut.codeHash(verificationId, CODE), hash)
    }

    @Test
    fun `should not store a code hash anyone can recompute without the secret`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val verificationId = Uuid.random()

        whenn()
        val hash = fixture.sut.codeHash(verificationId, CODE)

        then()
        assertNotEquals(sha256Hex("$verificationId:$CODE"), hash)
    }

    @Test
    fun `should match only the code that was hashed for that verification`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val verificationId = Uuid.random()
        val hash = fixture.sut.codeHash(verificationId, CODE)

        whenn()
        val matchesCode = fixture.sut.matches(hash, verificationId, CODE)
        val matchesOtherCode = fixture.sut.matches(hash, verificationId, "654321")
        val matchesOtherVerification = fixture.sut.matches(hash, Uuid.random(), CODE)

        then()
        assertTrue(matchesCode)
        assertFalse(matchesOtherCode)
        assertFalse(matchesOtherVerification)
    }

    @Test
    fun `should key an address the same way whatever its case`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val key = fixture.sut.addressKey("Client@Example.com")

        then()
        assertEquals(fixture.sut.addressKey("client@example.com"), key)
        assertNotEquals(sha256Hex("client@example.com"), key)
        assertNotEquals(SutFixture(SECRET.reversed()).sut.addressKey("client@example.com"), key)
    }

    @Test
    fun `should refuse a secret shorter than the hash`() = runUnitTest {
        given()
        val shortSecret = "a".repeat(EmailCodeHasher.MIN_SECRET_BYTES - 1)

        whenn()
        val result = runCatching { EmailCodeHasher.fromSecret(shortSecret) }

        then()
        assertTrue(result.exceptionOrNull() is IllegalArgumentException) { result.toString() }
    }

    private fun sha256Hex(value: String): String = MessageDigest.getInstance(
        "SHA-256",
    ).digest(value.toByteArray()).toHexString()

    private companion object {
        const val SECRET = "0123456789abcdef0123456789abcdef-test-secret"
        const val CODE = "123456"
    }
}
