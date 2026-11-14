package library.export.impl

import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.test.TestClock
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.encryption.Decrypted
import library.encryption.Decryptor
import library.encryption.SubjectKeys
import library.export.DataExport
import library.export.DataExportStatus
import library.export.DataExports
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DataExportsImplTest {

    private class SutFixture {
        val dataSource = mockk<DataExportDataSource>(relaxUnitFun = true)
        val subjectKeys = mockk<SubjectKeys>(relaxUnitFun = true)
        val decryptor = mockk<Decryptor>()
        val clock = TestClock()
        val ownerId = Uuid.random()
        val keyId = Uuid.random()
        val sut = DataExportsImpl(dataSource, subjectKeys, clock)

        fun storedDocument(ciphertext: ByteArray, decrypted: Decrypted<ByteArray>) {
            val document = StoredDataExportDocument(ciphertext, keyId)
            coEvery { dataSource.document(ownerId, clock.now) } returns document
            coEvery { subjectKeys.decryptor(listOf(keyId)) } returns decryptor
            coEvery { decryptor.decrypt(keyId, ciphertext) } returns decrypted
        }

        fun stored(record: DataExportRecord?) {
            coEvery { dataSource.getForUpdate(ownerId) } returns record
            coEvery { dataSource.get(ownerId) } returns record
        }

        fun record(
            state: DataExportState,
            requestedAgo: kotlin.time.Duration,
            expiresAt: Instant? = null,
        ) = DataExportRecord.stub(ownerId, state, clock.now - requestedAgo, attempts = 1, expiresAt = expiresAt)
    }

    @Test
    fun `should queue a first export and report it running`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stored(null)

        whenn()
        val export = fixture.sut.start(fixture.ownerId)

        then()
        coVerify(exactly = 1) { fixture.dataSource.insert(fixture.ownerId, fixture.clock.now) }
        assertEquals(
            DataExport(
                DataExportStatus.RUNNING,
                fixture.clock.now,
                expiresAt = null,
                availableAgainAt =
                fixture.clock.now + 1.days,
            ),
            export,
        )
    }

    @Test
    fun `should lock the owner's export before deciding`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stored(null)

        whenn()
        fixture.sut.start(fixture.ownerId)

        then()
        coVerify(exactly = 1) { fixture.dataSource.getForUpdate(fixture.ownerId) }
        coVerify(exactly = 0) { fixture.dataSource.get(any()) }
    }

    @Test
    fun `should return the export that is still being built instead of starting another`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val running = DataExportRecord.stub(
            fixture.ownerId,
            DataExportState.RUNNING,
            requestedAt =
            fixture.clock.now - 1.hours,
        )
        fixture.stored(running)

        whenn()
        val export = fixture.sut.start(fixture.ownerId)

        then()
        assertEquals(
            DataExport(
                DataExportStatus.RUNNING,
                running.requestedAt,
                expiresAt = null,
                availableAgainAt =
                running.requestedAt + 1.days,
            ),
            export,
        )
        coVerify(exactly = 0) { fixture.dataSource.insert(any(), any()) }
        coVerify(exactly = 0) { fixture.dataSource.restart(any(), any()) }
    }

    @Test
    fun `should return a queued export instead of starting another`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stored(
            DataExportRecord.stub(
                fixture.ownerId,
                DataExportState.PENDING,
                requestedAt =
                fixture.clock.now - 1.hours,
            ),
        )

        whenn()
        val export = fixture.sut.start(fixture.ownerId)

        then()
        assertEquals(DataExportStatus.RUNNING, export.status)
        coVerify(exactly = 0) { fixture.dataSource.restart(any(), any()) }
    }

    @Test
    fun `should refuse a second export within a day of the previous one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stored(
            DataExportRecord.stub(
                fixture.ownerId,
                DataExportState.READY,
                requestedAt =
                fixture.clock.now - 23.hours,
                expiresAt = fixture.clock.now + 49.hours,
            ),
        )

        whenn()
        val result = runSuspendCatching { fixture.sut.start(fixture.ownerId) }

        then()
        assertTrue(result.exceptionOrNull() is DataExports.Error.TooFrequent)
        coVerify(exactly = 0) { fixture.dataSource.restart(any(), any()) }
    }

    @Test
    fun `should start a new export a day after the previous one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stored(
            DataExportRecord.stub(
                fixture.ownerId,
                DataExportState.READY,
                requestedAt =
                fixture.clock.now - 1.days,
                expiresAt = fixture.clock.now + 48.hours,
            ),
        )

        whenn()
        val export = fixture.sut.start(fixture.ownerId)

        then()
        coVerify(exactly = 1) { fixture.dataSource.restart(fixture.ownerId, fixture.clock.now) }
        assertEquals(fixture.clock.now, export.requestedAt)
    }

    @Test
    fun `should shred the previous document's key when a new export starts`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val previous = DataExportRecord.stub(
            fixture.ownerId,
            DataExportState.READY,
            requestedAt = fixture.clock.now - 1.days,
            expiresAt = fixture.clock.now + 48.hours,
            keyId = fixture.keyId,
        )
        fixture.stored(previous)

        whenn()
        fixture.sut.start(fixture.ownerId)

        then()
        coVerify(exactly = 1) { fixture.subjectKeys.shred(fixture.keyId) }
    }

    @Test
    fun `should shred no key when the restarted export has no document`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stored(DataExportRecord.stub(fixture.ownerId, DataExportState.FAILED, keyId = null))

        whenn()
        fixture.sut.start(fixture.ownerId)

        then()
        coVerify(exactly = 1) { fixture.dataSource.restart(fixture.ownerId, fixture.clock.now) }
        coVerify(exactly = 0) { fixture.subjectKeys.shred(any()) }
    }

    @Test
    fun `should let a failed export be started again at once`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stored(
            DataExportRecord.stub(
                fixture.ownerId,
                DataExportState.FAILED,
                requestedAt =
                fixture.clock.now - 1.hours,
                expiresAt = fixture.clock.now + 71.hours,
            ),
        )

        whenn()
        val export = fixture.sut.start(fixture.ownerId)

        then()
        coVerify(exactly = 1) { fixture.dataSource.restart(fixture.ownerId, fixture.clock.now) }
        assertEquals(DataExportStatus.RUNNING, export.status)
    }

    @Test
    fun `should report no export for an owner who never asked for one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stored(null)

        whenn()
        val export = fixture.sut.status(fixture.ownerId)

        then()
        assertEquals(DataExport.NONE, export)
    }

    @Test
    fun `should report a ready export with its expiry and when the next one can start`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ready = DataExportRecord.stub(
            fixture.ownerId,
            DataExportState.READY,
            requestedAt =
            fixture.clock.now - 2.hours,
            expiresAt = fixture.clock.now + 70.hours,
        )
        fixture.stored(ready)

        whenn()
        val export = fixture.sut.status(fixture.ownerId)

        then()
        assertEquals(
            DataExport(
                DataExportStatus.READY,
                ready.requestedAt,
                ready.expiresAt,
                availableAgainAt =
                ready.requestedAt + 1.days,
            ),
            export,
        )
    }

    @Test
    fun `should report a ready export older than a day as open to a new one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stored(
            DataExportRecord.stub(
                fixture.ownerId,
                DataExportState.READY,
                requestedAt =
                fixture.clock.now - 30.hours,
                expiresAt = fixture.clock.now + 42.hours,
            ),
        )

        whenn()
        val export = fixture.sut.status(fixture.ownerId)

        then()
        assertEquals(DataExportStatus.READY, export.status)
        assertNull(export.availableAgainAt)
    }

    @Test
    fun `should report an expired export as no export`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.stored(
            DataExportRecord.stub(
                fixture.ownerId,
                DataExportState.READY,
                requestedAt =
                fixture.clock.now - 73.hours,
                expiresAt = fixture.clock.now,
            ),
        )

        whenn()
        val export = fixture.sut.status(fixture.ownerId)

        then()
        assertEquals(DataExport.NONE, export)
    }

    @Test
    fun `should report a failed export`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val failed = DataExportRecord.stub(
            fixture.ownerId,
            DataExportState.FAILED,
            requestedAt =
            fixture.clock.now - 1.hours,
            expiresAt = fixture.clock.now + 71.hours,
        )
        fixture.stored(failed)

        whenn()
        val export = fixture.sut.status(fixture.ownerId)

        then()
        assertEquals(
            DataExport(DataExportStatus.FAILED, failed.requestedAt, expiresAt = null, availableAgainAt = null),
            export,
        )
    }

    @Test
    fun `should hand out the stored document decrypted with its key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.storedDocument(byteArrayOf(9, 9, 9), Decrypted.Plain(byteArrayOf(4, 2)))

        whenn()
        val document = fixture.sut.document(fixture.ownerId)

        then()
        assertArrayEquals(byteArrayOf(4, 2), document?.gzippedJson)
    }

    @Test
    fun `should hand out nothing when the document's key was shredded`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.storedDocument(byteArrayOf(9, 9, 9), Decrypted.Shredded)

        whenn()
        val document = fixture.sut.document(fixture.ownerId)

        then()
        assertNull(document)
    }

    @Test
    fun `should hand out nothing when the document's key is missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.storedDocument(byteArrayOf(9, 9, 9), Decrypted.Missing)

        whenn()
        val document = fixture.sut.document(fixture.ownerId)

        then()
        assertNull(document)
    }

    @Test
    fun `should never hand out a document stored without a key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery {
            fixture.dataSource.document(fixture.ownerId, fixture.clock.now)
        } returns StoredDataExportDocument(byteArrayOf(4, 2), keyId = null)

        whenn()
        val document = fixture.sut.document(fixture.ownerId)

        then()
        assertNull(document)
        coVerify(exactly = 0) { fixture.subjectKeys.decryptor(any()) }
    }

    @Test
    fun `should hand out nothing when no document is ready`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.dataSource.document(fixture.ownerId, fixture.clock.now) } returns null

        whenn()
        val document = fixture.sut.document(fixture.ownerId)

        then()
        assertNull(document)
    }

    @Test
    fun `should discard the owner's export and shred its document's key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.dataSource.delete(setOf(fixture.ownerId)) } returns listOf(fixture.keyId)

        whenn()
        fixture.sut.discard(fixture.ownerId)

        then()
        coVerify(exactly = 1) { fixture.dataSource.delete(setOf(fixture.ownerId)) }
        coVerify(exactly = 1) { fixture.subjectKeys.shredAll(listOf(fixture.keyId)) }
    }

    @Test
    fun `should discard several owners' exports with one delete and one shred`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val other = Uuid.random()
        val keyIds = listOf(Uuid.random(), Uuid.random())
        coEvery { fixture.dataSource.delete(setOf(fixture.ownerId, other)) } returns keyIds

        whenn()
        fixture.sut.discardAll(listOf(fixture.ownerId, other, fixture.ownerId))

        then()
        coVerify(exactly = 1) { fixture.dataSource.delete(any()) }
        coVerify(exactly = 1) { fixture.subjectKeys.shredAll(keyIds) }
    }

    @Test
    fun `should touch nothing when discarding no exports`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.discardAll(emptyList())

        then()
        coVerify(exactly = 0) { fixture.dataSource.delete(any()) }
        coVerify(exactly = 0) { fixture.subjectKeys.shredAll(any()) }
    }

    @Test
    fun `should shred no key when the discarded export had no document`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.dataSource.delete(setOf(fixture.ownerId)) } returns emptyList()

        whenn()
        fixture.sut.discard(fixture.ownerId)

        then()
        coVerify(exactly = 0) { fixture.subjectKeys.shredAll(any()) }
    }
}
