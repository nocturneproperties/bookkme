package library.export.impl

import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.export.DataExports
import library.export.impl.orm.DataExportTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DataExportDataSourceTest {

    private class SutFixture {
        val db = createTestDatabase(DataExportTable)
        val sut = DataExportDataSource()
        val ownerId = Uuid.random()
        val now = Instant.fromEpochMilliseconds(1_800_000_000_000)

        suspend fun record(ownerId: Uuid = this.ownerId) = suspendTransaction { sut.get(ownerId) }

        suspend fun document(ownerId: Uuid = this.ownerId): ByteArray? = suspendTransaction {
            DataExportTable.selectAll().where { DataExportTable.ownerId eq ownerId }.single()[DataExportTable.document]?.bytes
        }

        suspend fun requested(ownerId: Uuid = this.ownerId, at: Instant = now) = suspendTransaction { sut.insert(ownerId, at) }

        suspend fun claimed(ownerId: Uuid = this.ownerId, claimId: Uuid = Uuid.random(), at: Instant = now): Uuid {
            requested(ownerId, at)
            suspendTransaction { sut.claim(ownerId, claimId, at, at + DataExports.BUILD_LEASE) }
            return claimId
        }

        suspend fun ready(ownerId: Uuid = this.ownerId, at: Instant = now, expiresAt: Instant = at + DataExports.DOCUMENT_LIFETIME) {
            val claimId = claimed(ownerId, at = at)
            suspendTransaction { sut.complete(ownerId, claimId, byteArrayOf(1, 2, 3), at, expiresAt) }
        }

        suspend fun attempts(ownerId: Uuid = this.ownerId, count: Int) = suspendTransaction {
            DataExportTable.update({ DataExportTable.ownerId eq ownerId }) { it[attempts] = count }
        }
    }

    @Test
    fun `should store a requested export as pending with no attempts`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.requested()

        then()
        assertEquals(DataExportRecord(fixture.ownerId, DataExportState.PENDING, fixture.now, attempts = 0, expiresAt = null), fixture.record())
    }

    @Test
    fun `should return nothing for an owner who never requested an export`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val record = suspendTransaction { fixture.sut.getForUpdate(fixture.ownerId) }

        then()
        assertNull(record)
    }

    @Test
    fun `should lock and return the owner's export`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.requested()

        whenn()
        val record = suspendTransaction { fixture.sut.getForUpdate(fixture.ownerId) }

        then()
        assertEquals(DataExportState.PENDING, record?.state)
    }

    @Test
    fun `should restart a finished export from scratch`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.ready()
        val restartedAt = fixture.now + 30.hours

        whenn()
        suspendTransaction { fixture.sut.restart(fixture.ownerId, restartedAt) }

        then()
        assertEquals(DataExportRecord(fixture.ownerId, DataExportState.PENDING, restartedAt, attempts = 0, expiresAt = null), fixture.record())
        assertNull(fixture.document())
    }

    @Test
    fun `should list pending exports and running ones whose build lease ran out`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val pending = Uuid.random().also { fixture.requested(it) }
        val abandoned = Uuid.random().also { fixture.claimed(it, at = fixture.now - 11.minutes) }
        Uuid.random().also { fixture.claimed(it, at = fixture.now - 1.minutes) }
        Uuid.random().also { fixture.ready(it) }

        whenn()
        val claimable = suspendTransaction { fixture.sut.claimable(fixture.now) }

        then()
        assertEquals(setOf(pending, abandoned), claimable.toSet())
    }

    @Test
    fun `should claim a pending export and count the attempt`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.requested()

        whenn()
        val claimed = suspendTransaction { fixture.sut.claim(fixture.ownerId, Uuid.random(), fixture.now, fixture.now + DataExports.BUILD_LEASE) }

        then()
        assertTrue(claimed)
        assertEquals(DataExportState.RUNNING, fixture.record()?.state)
        assertEquals(1, fixture.record()?.attempts)
    }

    @Test
    fun `should not claim an export another build holds`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claimed(at = fixture.now - 1.minutes)

        whenn()
        val claimed = suspendTransaction { fixture.sut.claim(fixture.ownerId, Uuid.random(), fixture.now, fixture.now + DataExports.BUILD_LEASE) }

        then()
        assertFalse(claimed)
        assertEquals(1, fixture.record()?.attempts)
    }

    @Test
    fun `should claim an export whose build lease ran out`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claimed(at = fixture.now - 11.minutes)

        whenn()
        val claimed = suspendTransaction { fixture.sut.claim(fixture.ownerId, Uuid.random(), fixture.now, fixture.now + DataExports.BUILD_LEASE) }

        then()
        assertTrue(claimed)
        assertEquals(2, fixture.record()?.attempts)
    }

    @Test
    fun `should not claim an export that used up its attempts`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claimed(at = fixture.now - 11.minutes)
        fixture.attempts(count = DataExports.MAX_BUILD_ATTEMPTS)

        whenn()
        val claimed = suspendTransaction { fixture.sut.claim(fixture.ownerId, Uuid.random(), fixture.now, fixture.now + DataExports.BUILD_LEASE) }

        then()
        assertFalse(claimed)
    }

    @Test
    fun `should store the document of the build that holds the claim`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val claimId = fixture.claimed()
        val expiresAt = fixture.now + DataExports.DOCUMENT_LIFETIME

        whenn()
        val completed = suspendTransaction { fixture.sut.complete(fixture.ownerId, claimId, byteArrayOf(7, 8), fixture.now, expiresAt) }

        then()
        assertTrue(completed)
        assertEquals(DataExportRecord(fixture.ownerId, DataExportState.READY, fixture.now, attempts = 1, expiresAt = expiresAt), fixture.record())
        assertArrayEquals(byteArrayOf(7, 8), fixture.document())
    }

    @Test
    fun `should drop the document of a build that lost its claim`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claimed()

        whenn()
        val completed = suspendTransaction { fixture.sut.complete(fixture.ownerId, Uuid.random(), byteArrayOf(7, 8), fixture.now, fixture.now + DataExports.DOCUMENT_LIFETIME) }

        then()
        assertFalse(completed)
        assertEquals(DataExportState.RUNNING, fixture.record()?.state)
        assertNull(fixture.document())
    }

    @Test
    fun `should drop the document of an export deleted during its build`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val claimId = fixture.claimed()
        suspendTransaction { fixture.sut.delete(fixture.ownerId) }

        whenn()
        val completed = suspendTransaction { fixture.sut.complete(fixture.ownerId, claimId, byteArrayOf(7, 8), fixture.now, fixture.now + DataExports.DOCUMENT_LIFETIME) }

        then()
        assertFalse(completed)
        assertNull(fixture.record())
    }

    @Test
    fun `should put a failed build back in the queue while attempts remain`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val claimId = fixture.claimed()

        whenn()
        suspendTransaction { fixture.sut.giveBack(fixture.ownerId, claimId, fixture.now, fixture.now + DataExports.DOCUMENT_LIFETIME) }

        then()
        assertEquals(DataExportState.PENDING, fixture.record()?.state)
        assertEquals(listOf(fixture.ownerId), suspendTransaction { fixture.sut.claimable(fixture.now) })
    }

    @Test
    fun `should mark an export failed when its last attempt fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val claimId = fixture.claimed()
        fixture.attempts(count = DataExports.MAX_BUILD_ATTEMPTS)
        val expiresAt = fixture.now + DataExports.DOCUMENT_LIFETIME

        whenn()
        suspendTransaction { fixture.sut.giveBack(fixture.ownerId, claimId, fixture.now, expiresAt) }

        then()
        assertEquals(DataExportRecord(fixture.ownerId, DataExportState.FAILED, fixture.now, attempts = DataExports.MAX_BUILD_ATTEMPTS, expiresAt = expiresAt), fixture.record())
        assertTrue(suspendTransaction { fixture.sut.claimable(fixture.now) }.isEmpty())
    }

    @Test
    fun `should not touch an export another build claimed in the meantime when giving back`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claimed()

        whenn()
        suspendTransaction { fixture.sut.giveBack(fixture.ownerId, Uuid.random(), fixture.now, fixture.now + DataExports.DOCUMENT_LIFETIME) }

        then()
        assertEquals(DataExportState.RUNNING, fixture.record()?.state)
    }

    @Test
    fun `should fail an abandoned build that used up its attempts`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val exhausted = Uuid.random().also { fixture.claimed(it, at = fixture.now - 11.minutes) }
        fixture.attempts(exhausted, DataExports.MAX_BUILD_ATTEMPTS)
        val retryable = Uuid.random().also { fixture.claimed(it, at = fixture.now - 11.minutes) }
        val expiresAt = fixture.now + DataExports.DOCUMENT_LIFETIME

        whenn()
        suspendTransaction { fixture.sut.failAbandoned(fixture.now, expiresAt) }

        then()
        assertEquals(DataExportState.FAILED, fixture.record(exhausted)?.state)
        assertEquals(expiresAt, fixture.record(exhausted)?.expiresAt)
        assertEquals(DataExportState.RUNNING, fixture.record(retryable)?.state)
    }

    @Test
    fun `should hand out the document of a ready export until it expires`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.ready(expiresAt = fixture.now + 1.hours)

        whenn()
        val beforeExpiry = suspendTransaction { fixture.sut.document(fixture.ownerId, fixture.now) }
        val afterExpiry = suspendTransaction { fixture.sut.document(fixture.ownerId, fixture.now + 1.hours) }

        then()
        assertArrayEquals(byteArrayOf(1, 2, 3), beforeExpiry)
        assertNull(afterExpiry)
    }

    @Test
    fun `should hand out no document for an export that is still running`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claimed()

        whenn()
        val document = suspendTransaction { fixture.sut.document(fixture.ownerId, fixture.now) }

        then()
        assertNull(document)
    }

    @Test
    fun `should delete only the owner's export`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val other = Uuid.random()
        fixture.ready()
        fixture.ready(other)

        whenn()
        suspendTransaction { fixture.sut.delete(fixture.ownerId) }

        then()
        assertNull(fixture.record())
        assertEquals(DataExportState.READY, fixture.record(other)?.state)
    }

    @Test
    fun `should delete expired exports and keep the rest`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val expired = Uuid.random().also { fixture.ready(it, expiresAt = fixture.now) }
        val ready = Uuid.random().also { fixture.ready(it, expiresAt = fixture.now + 1.minutes) }
        val running = Uuid.random().also { fixture.claimed(it) }

        whenn()
        suspendTransaction { fixture.sut.deleteExpired(fixture.now) }

        then()
        assertNull(fixture.record(expired))
        assertEquals(DataExportState.READY, fixture.record(ready)?.state)
        assertEquals(DataExportState.RUNNING, fixture.record(running)?.state)
    }
}
