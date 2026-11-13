package library.encryption.impl

import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.encryption.impl.orm.SubjectKeyTable
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SubjectKeyDataSourceTest {

    private class SutFixture {
        val table = SubjectKeyTable(SCHEMA)
        val db = createTestDatabase().also { db ->
            transaction(db) {
                SchemaUtils.createSchema(Schema(SCHEMA))
                SchemaUtils.create(table)
            }
        }
        val sut = SubjectKeyDataSource(table)
        val shreddedAt = Instant.fromEpochMilliseconds(1_900_000_000_000)

        fun key(subjectId: Uuid = Uuid.random(), version: Int = 1): StoredSubjectKey.Live {
            return StoredSubjectKey.Live(
                subjectId = subjectId,
                wrappedKey = ByteArray(StoredSubjectKey.WRAPPED_KEY_LENGTH).also(SecureRandom()::nextBytes),
                masterKeyVersion = version,
                createdAt = Instant.fromEpochMilliseconds(1_800_000_000_000),
            )
        }

        suspend fun read(subjectId: Uuid): StoredSubjectKey? {
            return suspendTransaction { sut.get(subjectId) }
        }
    }

    @Test
    fun `should store a new subject key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val key = fixture.key(version = 3)

        whenn()
        val stored = suspendTransaction { fixture.sut.insertIfAbsent(key) }

        then()
        val read = fixture.read(key.subjectId)
        listOf(stored, read).forEach {
            assertTrue(it is StoredSubjectKey.Live) { it.toString() }
            it as StoredSubjectKey.Live
            assertEquals(key.subjectId, it.subjectId)
            assertArrayEquals(key.wrappedKey, it.wrappedKey)
            assertEquals(3, it.masterKeyVersion)
            assertEquals(key.createdAt, it.createdAt)
        }
    }

    @Test
    fun `should keep the existing key when the subject already has one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existing = fixture.key()
        suspendTransaction { fixture.sut.insertIfAbsent(existing) }

        whenn()
        val stored = suspendTransaction { fixture.sut.insertIfAbsent(fixture.key(subjectId = existing.subjectId)) }

        then()
        assertArrayEquals(existing.wrappedKey, (stored as StoredSubjectKey.Live).wrappedKey)
        assertArrayEquals(existing.wrappedKey, (fixture.read(existing.subjectId) as StoredSubjectKey.Live).wrappedKey)
    }

    @Test
    fun `should return no key for a subject without one`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val read = fixture.read(Uuid.random())

        then()
        assertNull(read)
    }

    @Test
    fun `should read only the requested subjects' keys and tombstones`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = fixture.key()
        val second = fixture.key()
        val shredded = Uuid.random()
        suspendTransaction {
            listOf(first, second, fixture.key()).forEach { fixture.sut.insertIfAbsent(it) }
            fixture.sut.shred(shredded, fixture.shreddedAt)
            fixture.sut.shred(Uuid.random(), fixture.shreddedAt)
        }

        whenn()
        val read = suspendTransaction {
            fixture.sut.getAll(setOf(first.subjectId, second.subjectId, shredded, Uuid.random()))
        }

        then()
        assertEquals(setOf(first.subjectId, second.subjectId, shredded), read.map { it.subjectId }.toSet())
        assertTrue(read.single { it.subjectId == shredded } is StoredSubjectKey.Shredded)
    }

    @Test
    fun `should read nothing for no subjects`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction { fixture.sut.insertIfAbsent(fixture.key()) }

        whenn()
        val read = suspendTransaction { fixture.sut.getAll(emptySet()) }

        then()
        assertEquals(emptyList<StoredSubjectKey>(), read)
    }

    @Test
    fun `should replace only the shredded subject's key with a tombstone`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val shredded = fixture.key()
        val kept = fixture.key()
        suspendTransaction { listOf(shredded, kept).forEach { fixture.sut.insertIfAbsent(it) } }

        whenn()
        suspendTransaction { fixture.sut.shred(shredded.subjectId, fixture.shreddedAt) }

        then()
        val tombstone = fixture.read(shredded.subjectId)
        assertTrue(tombstone is StoredSubjectKey.Shredded) { tombstone.toString() }
        assertEquals(fixture.shreddedAt, (tombstone as StoredSubjectKey.Shredded).shreddedAt)
        assertArrayEquals(kept.wrappedKey, (fixture.read(kept.subjectId) as StoredSubjectKey.Live).wrappedKey)
    }

    @Test
    fun `should leave a tombstone for a subject that never had a key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val subjectId = Uuid.random()

        whenn()
        suspendTransaction { fixture.sut.shred(subjectId, fixture.shreddedAt) }

        then()
        val tombstone = fixture.read(subjectId)
        assertTrue(tombstone is StoredSubjectKey.Shredded) { tombstone.toString() }
        assertEquals(fixture.shreddedAt, (tombstone as StoredSubjectKey.Shredded).shreddedAt)
    }

    @Test
    fun `should keep the first shredding time when a subject is shredded again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val key = fixture.key()
        suspendTransaction {
            fixture.sut.insertIfAbsent(key)
            fixture.sut.shred(key.subjectId, fixture.shreddedAt)
        }

        whenn()
        suspendTransaction { fixture.sut.shred(key.subjectId, fixture.shreddedAt + 1.hours) }

        then()
        assertEquals(fixture.shreddedAt, (fixture.read(key.subjectId) as StoredSubjectKey.Shredded).shreddedAt)
    }

    @Test
    fun `should not store a new key for a shredded subject`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val key = fixture.key()
        suspendTransaction { fixture.sut.shred(key.subjectId, fixture.shreddedAt) }

        whenn()
        val stored = suspendTransaction { fixture.sut.insertIfAbsent(key) }

        then()
        assertTrue(stored is StoredSubjectKey.Shredded) { stored.toString() }
        assertTrue(fixture.read(key.subjectId) is StoredSubjectKey.Shredded)
    }

    private companion object {
        const val SCHEMA = "test_keys"
    }
}
