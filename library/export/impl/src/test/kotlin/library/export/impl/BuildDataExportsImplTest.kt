package library.export.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.TestClock
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import library.encryption.Encryptor
import library.encryption.SubjectKeys
import library.export.DataExportAssembler
import library.export.DataExports
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class BuildDataExportsImplTest {

    private class SutFixture {
        val dataSource = mockk<DataExportDataSource>(relaxUnitFun = true)
        val assembler = mockk<DataExportAssembler>()
        val transactionManager = mockk<TransactionManager>()
        val subjectKeys = mockk<SubjectKeys>(relaxUnitFun = true)
        val clock = TestClock()
        val sut = BuildDataExportsImpl(transactionManager, dataSource, assembler, subjectKeys, clock)
        val storedDocuments = mutableMapOf<Uuid, String>()
        val encryptedUnder = mutableListOf<Uuid>()
        val storedUnder = mutableMapOf<Uuid, Uuid>()
        val assembledInTransaction = mutableListOf<Boolean>()
        val announcedInTransaction = mutableListOf<Boolean>()
        val announced = mutableMapOf<Uuid, Instant>()

        init {
            transactionManager.mockTransaction()
            coEvery { dataSource.claimable(any()) } returns emptyList()
            coEvery { dataSource.claim(any(), any(), any(), any()) } returns true
            coEvery { subjectKeys.encryptor(capture(encryptedUnder)) } coAnswers { TaggingEncryptor(firstArg()) }
            coEvery { dataSource.complete(any(), any(), any(), any(), any(), any()) } coAnswers {
                val keyId = arg<Uuid>(3)
                storedDocuments[firstArg()] = gunzip(TaggingEncryptor(keyId).untag(thirdArg()))
                storedUnder[firstArg()] = keyId
                true
            }
            coEvery { assembler.announceReady(any(), any()) } coAnswers {
                announced[firstArg()] = secondArg()
                announcedInTransaction += transactionManager.isInTransaction()
            }
        }

        fun queued(vararg ownerIds: Uuid) {
            coEvery { dataSource.claimable(clock.now) } returns ownerIds.toList()
        }

        fun assembles(ownerId: Uuid, json: String) {
            coEvery { assembler.assemble(ownerId) } coAnswers {
                assembledInTransaction += transactionManager.isInTransaction()
                json
            }
        }

        private fun gunzip(bytes: ByteArray): String = GZIPInputStream(
            ByteArrayInputStream(bytes),
        ).readBytes().decodeToString()
    }

    private class TaggingEncryptor(keyId: Uuid) : Encryptor {
        private val tag = keyId.toByteArray()

        override fun encrypt(plaintext: String): String {
            throw UnsupportedOperationException("Export documents are encrypted as bytes")
        }

        override fun encrypt(plaintext: ByteArray): ByteArray {
            return tag + plaintext
        }

        fun untag(ciphertext: ByteArray): ByteArray {
            check(ciphertext.copyOfRange(0, tag.size).contentEquals(tag)) { "Encrypted under another key" }
            return ciphertext.copyOfRange(tag.size, ciphertext.size)
        }
    }

    @Test
    fun `should store each queued export gzipped and announce it with its expiry`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = Uuid.random()
        val second = Uuid.random()
        fixture.queued(first, second)
        fixture.assembles(first, """{"first":true}""")
        fixture.assembles(second, """{"second":true}""")

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        assertEquals(mapOf(first to """{"first":true}""", second to """{"second":true}"""), fixture.storedDocuments)
        val expiresAt = fixture.clock.now + DataExports.DOCUMENT_LIFETIME
        assertEquals(mapOf(first to expiresAt, second to expiresAt), fixture.announced)
        coVerify(exactly = 1) { fixture.dataSource.complete(first, any(), any(), any(), fixture.clock.now, expiresAt) }
    }

    @Test
    fun `should encrypt each document under a new key of its own`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = Uuid.random()
        val second = Uuid.random()
        fixture.queued(first, second)
        fixture.assembles(first, "{}")
        fixture.assembles(second, "{}")

        whenn()
        fixture.sut()

        then()
        assertEquals(2, fixture.encryptedUnder.toSet().size)
        assertEquals(fixture.encryptedUnder.toSet(), fixture.storedUnder.values.toSet())
        assertTrue(fixture.encryptedUnder.none { it == first || it == second })
    }

    @Test
    fun `should shred the new key when the export was deleted during its build`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ownerId = Uuid.random()
        fixture.queued(ownerId)
        fixture.assembles(ownerId, "{}")
        coEvery { fixture.dataSource.complete(ownerId, any(), any(), any(), any(), any()) } returns false

        whenn()
        fixture.sut()

        then()
        coVerify(exactly = 1) { fixture.subjectKeys.shred(fixture.encryptedUnder.single()) }
    }

    @Test
    fun `should give the build back when its new key can't encrypt`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ownerId = Uuid.random()
        fixture.queued(ownerId)
        fixture.assembles(ownerId, "{}")
        coEvery { fixture.subjectKeys.encryptor(any()) } returns null

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.dataSource.complete(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { fixture.dataSource.giveBack(ownerId, any(), any(), any()) }
    }

    @Test
    fun `should assemble outside a transaction and announce inside the one that stores the document`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ownerId = Uuid.random()
        fixture.queued(ownerId)
        fixture.assembles(ownerId, "{}")

        whenn()
        fixture.sut()

        then()
        assertEquals(listOf(false), fixture.assembledInTransaction)
        assertEquals(listOf(true), fixture.announcedInTransaction)
    }

    @Test
    fun `should fail abandoned builds before listing the queue`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut()

        then()
        coVerifyOrder {
            fixture.dataSource.failAbandoned(fixture.clock.now, fixture.clock.now + DataExports.DOCUMENT_LIFETIME)
            fixture.dataSource.claimable(fixture.clock.now)
        }
    }

    @Test
    fun `should claim an export with a lease before assembling it and complete it under that claim`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ownerId = Uuid.random()
        fixture.queued(ownerId)
        fixture.assembles(ownerId, "{}")
        val claimIds = mutableListOf<Uuid>()
        coEvery {
            fixture.dataSource.claim(
                ownerId,
                capture(claimIds),
                fixture.clock.now,
                fixture.clock.now + DataExports.BUILD_LEASE,
            )
        } returns true

        whenn()
        fixture.sut()

        then()
        coVerifyOrder {
            fixture.dataSource.claim(
                ownerId,
                claimIds.single(),
                fixture.clock.now,
                fixture.clock.now + DataExports.BUILD_LEASE,
            )
            fixture.assembler.assemble(ownerId)
            fixture.dataSource.complete(ownerId, claimIds.single(), any(), any(), any(), any())
        }
    }

    @Test
    fun `should skip an export another instance claimed first`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ownerId = Uuid.random()
        fixture.queued(ownerId)
        coEvery { fixture.dataSource.claim(ownerId, any(), any(), any()) } returns false

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.assembler.assemble(any()) }
    }

    @Test
    fun `should give a failed build back under its claim and still build the others`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val failing = Uuid.random()
        val working = Uuid.random()
        fixture.queued(failing, working)
        coEvery { fixture.assembler.assemble(failing) } throws IllegalStateException("user service unreachable")
        fixture.assembles(working, "{}")
        val claimIds = mutableListOf<Uuid>()
        coEvery { fixture.dataSource.claim(failing, capture(claimIds), any(), any()) } returns true

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.dataSource.giveBack(
                failing,
                claimIds.single(),
                fixture.clock.now,
                fixture.clock.now + DataExports.DOCUMENT_LIFETIME,
            )
        }
        coVerify(exactly = 0) { fixture.dataSource.complete(failing, any(), any(), any(), any(), any()) }
        assertEquals(setOf(working), fixture.storedDocuments.keys)
    }

    @Test
    fun `should give the build back when the document can't be stored`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ownerId = Uuid.random()
        fixture.queued(ownerId)
        fixture.assembles(ownerId, "{}")
        coEvery { fixture.dataSource.complete(ownerId, any(), any(), any(), any(), any()) } throws
            IllegalStateException("packet too large")

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.dataSource.giveBack(ownerId, any(), any(), any()) }
        assertTrue(fixture.announced.isEmpty())
    }

    @Test
    fun `should announce nothing when the export was deleted during its build`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ownerId = Uuid.random()
        fixture.queued(ownerId)
        fixture.assembles(ownerId, "{}")
        coEvery { fixture.dataSource.complete(ownerId, any(), any(), any(), any(), any()) } returns false

        whenn()
        fixture.sut()

        then()
        assertTrue(fixture.announced.isEmpty())
        coVerify(exactly = 0) { fixture.dataSource.giveBack(any(), any(), any(), any()) }
    }

    @Test
    fun `should fail when the queue can't be read`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.dataSource.claimable(any()) } throws IllegalStateException("database unreachable")

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isFailure)
    }
}
