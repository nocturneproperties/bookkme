package library.encryption.impl

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SubjectKeysImplTest {

    private class SutFixture {
        val dataSource = mockk<SubjectKeyDataSource>(relaxUnitFun = true)
        val masterKey = MasterKey.fromBase64(version = 1, encoded = randomBase64Key())
        val now = Instant.fromEpochMilliseconds(1_800_000_000_000)
        val clock = object : Clock {
            override fun now(): Instant {
                return this@SutFixture.now
            }
        }
        val sut = SubjectKeysImpl(dataSource, masterKey, SecureRandom(), clock)
        val subjectId = Uuid.random()

        fun storedKey(subjectId: Uuid = this.subjectId): StoredSubjectKey {
            return StoredSubjectKey(
                subjectId = subjectId,
                wrappedKey = masterKey.wrap(subjectId, randomSecretKey()),
                masterKeyVersion = masterKey.version,
                createdAt = now,
            )
        }
    }

    @Test
    fun `should create a wrapped key for a subject that has none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val inserted = slot<StoredSubjectKey>()
        with(fixture) {
            coEvery { dataSource.get(subjectId) } returns null
            coEvery { dataSource.insertIfAbsent(capture(inserted)) } answers { inserted.captured }
        }

        whenn()
        val ciphertext = fixture.sut.encryptor(fixture.subjectId).encrypt(PLAINTEXT)

        then()
        assertEquals(fixture.subjectId, inserted.captured.subjectId)
        assertEquals(fixture.masterKey.version, inserted.captured.masterKeyVersion)
        assertEquals(fixture.now, inserted.captured.createdAt)
        val insertedKey = fixture.masterKey.unwrap(inserted.captured)
        assertEquals(PLAINTEXT, FieldCipher.decrypt(insertedKey, fixture.subjectId, ciphertext))
    }

    @Test
    fun `should encrypt with the key another writer stored first`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val winner = fixture.storedKey()
        with(fixture) {
            coEvery { dataSource.get(subjectId) } returns null
            coEvery { dataSource.insertIfAbsent(any()) } returns winner
        }

        whenn()
        val ciphertext = fixture.sut.encryptor(fixture.subjectId).encrypt(PLAINTEXT)

        then()
        assertEquals(PLAINTEXT, FieldCipher.decrypt(fixture.masterKey.unwrap(winner), fixture.subjectId, ciphertext))
    }

    @Test
    fun `should reuse the subject's existing key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existing = fixture.storedKey()
        coEvery { fixture.dataSource.get(fixture.subjectId) } returns existing

        whenn()
        val ciphertext = fixture.sut.encryptor(fixture.subjectId).encrypt(PLAINTEXT)

        then()
        coVerify(exactly = 0) { fixture.dataSource.insertIfAbsent(any()) }
        assertEquals(PLAINTEXT, FieldCipher.decrypt(fixture.masterKey.unwrap(existing), fixture.subjectId, ciphertext))
    }

    @Test
    fun `should decrypt every requested subject's fields with one lookup`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val other = Uuid.random()
        val keys = listOf(fixture.storedKey(), fixture.storedKey(other))
        coEvery { fixture.dataSource.get(any()) } answers { keys.single { it.subjectId == firstArg() } }
        coEvery { fixture.dataSource.getAll(setOf(fixture.subjectId, other)) } returns keys
        val mine = fixture.sut.encryptor(fixture.subjectId).encrypt(PLAINTEXT)
        val theirs = fixture.sut.encryptor(other).encrypt("john@example.com")

        whenn()
        val decryptor = fixture.sut.decryptor(listOf(fixture.subjectId, other, fixture.subjectId))

        then()
        assertEquals(PLAINTEXT, decryptor.decrypt(fixture.subjectId, mine))
        assertEquals("john@example.com", decryptor.decrypt(other, theirs))
        coVerify(exactly = 1) { fixture.dataSource.getAll(any()) }
    }

    @Test
    fun `should read a shredded subject's field as missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existing = fixture.storedKey()
        coEvery { fixture.dataSource.get(fixture.subjectId) } returns existing
        val ciphertext = fixture.sut.encryptor(fixture.subjectId).encrypt(PLAINTEXT)
        coEvery { fixture.dataSource.getAll(setOf(fixture.subjectId)) } returns emptyList()

        whenn()
        val plaintext = fixture.sut.decryptor(listOf(fixture.subjectId)).decrypt(fixture.subjectId, ciphertext)

        then()
        assertNull(plaintext)
    }

    @Test
    fun `should refuse to decrypt a subject that was not requested`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.dataSource.getAll(setOf(fixture.subjectId)) } returns listOf(fixture.storedKey())

        whenn()
        val decryptor = fixture.sut.decryptor(listOf(fixture.subjectId))
        val result = runCatching { decryptor.decrypt(Uuid.random(), "irrelevant") }

        then()
        assertTrue(result.exceptionOrNull() is IllegalArgumentException) { result.toString() }
    }

    @Test
    fun `should not look up keys when no subjects are requested`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.decryptor(emptyList())

        then()
        coVerify(exactly = 0) { fixture.dataSource.getAll(any()) }
    }

    @Test
    fun `should give different subjects different keys`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val inserted = mutableListOf<StoredSubjectKey>()
        coEvery { fixture.dataSource.get(any()) } returns null
        coEvery { fixture.dataSource.insertIfAbsent(capture(inserted)) } answers { inserted.last() }

        whenn()
        fixture.sut.encryptor(fixture.subjectId)
        fixture.sut.encryptor(Uuid.random())

        then()
        val (first, second) = inserted.map { fixture.masterKey.unwrap(it).encoded.toList() }
        assertNotEquals(first, second)
    }

    @Test
    fun `should delete the shredded subject's key`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.shred(fixture.subjectId)

        then()
        coVerify(exactly = 1) { fixture.dataSource.delete(fixture.subjectId) }
    }

    private companion object {
        const val PLAINTEXT = "jane.doe@example.com"
    }
}
