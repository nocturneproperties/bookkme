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
import org.junit.jupiter.api.Test
import java.security.SecureRandom
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

        fun key(subjectId: Uuid = Uuid.random(), version: Int = 1): StoredSubjectKey {
            return StoredSubjectKey(
                subjectId = subjectId,
                wrappedKey = ByteArray(StoredSubjectKey.WRAPPED_KEY_LENGTH).also(SecureRandom()::nextBytes),
                masterKeyVersion = version,
                createdAt = Instant.fromEpochMilliseconds(1_800_000_000_000),
            )
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
        val read = suspendTransaction { fixture.sut.get(key.subjectId) }!!
        listOf(stored, read).forEach {
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
        assertArrayEquals(existing.wrappedKey, stored.wrappedKey)
        assertArrayEquals(existing.wrappedKey, suspendTransaction { fixture.sut.get(existing.subjectId) }!!.wrappedKey)
    }

    @Test
    fun `should return no key for a subject without one`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val read = suspendTransaction { fixture.sut.get(Uuid.random()) }

        then()
        assertNull(read)
    }

    @Test
    fun `should read only the requested subjects' keys`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = fixture.key()
        val second = fixture.key()
        suspendTransaction { listOf(first, second, fixture.key()).forEach { fixture.sut.insertIfAbsent(it) } }

        whenn()
        val read = suspendTransaction { fixture.sut.getAll(setOf(first.subjectId, second.subjectId, Uuid.random())) }

        then()
        assertEquals(setOf(first.subjectId, second.subjectId), read.map { it.subjectId }.toSet())
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
    fun `should delete only the shredded subject's key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val shredded = fixture.key()
        val kept = fixture.key()
        suspendTransaction { listOf(shredded, kept).forEach { fixture.sut.insertIfAbsent(it) } }

        whenn()
        suspendTransaction { fixture.sut.delete(shredded.subjectId) }

        then()
        assertNull(suspendTransaction { fixture.sut.get(shredded.subjectId) })
        assertEquals(kept.subjectId, suspendTransaction { fixture.sut.get(kept.subjectId) }?.subjectId)
    }

    private companion object {
        const val SCHEMA = "test_keys"
    }
}
