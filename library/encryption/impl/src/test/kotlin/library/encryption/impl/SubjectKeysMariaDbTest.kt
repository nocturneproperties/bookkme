package library.encryption.impl

import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.database.createDatabase
import com.bookk.core.test.TestClock
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import library.encryption.Decrypted
import library.encryption.Encryptor
import library.encryption.impl.orm.SubjectKeyTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.security.SecureRandom
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

internal class SubjectKeysMariaDbTest {

    companion object {
        private val SLOWER_THAN_INNODB_TRX_CACHE_REFRESH = 150.milliseconds
        private const val PASSWORD = "test-password"
        private const val PORT = 3306
        private const val KEY_SCHEMA = "keys_test_keys"

        private val container = GenericContainer(DockerImageName.parse("mariadb:11.8.3-ubi9"))
            .withEnv("MARIADB_ROOT_PASSWORD", PASSWORD)
            .withExposedPorts(PORT)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*", 2))

        private lateinit var database: Database

        @JvmStatic
        @BeforeAll
        fun startMariaDb() {
            container.start()
            database = createDatabase(
                schemaName = "keys_test",
                dbUrl = "mariadb://${container.host}",
                dbPort = container.getMappedPort(PORT).toString(),
                dbUsername = "root",
                dbPassword = PASSWORD,
            )
            transaction(database) {
                exec("CREATE DATABASE IF NOT EXISTS $KEY_SCHEMA")
                exec(
                    "CREATE TABLE IF NOT EXISTS $KEY_SCHEMA.subject_key (" +
                        "subject_id BINARY(16) NOT NULL PRIMARY KEY, " +
                        "wrapped_key VARBINARY(60) NULL, " +
                        "master_key_version INT NULL, " +
                        "created_at TIMESTAMP(6) NOT NULL, " +
                        "shredded_at TIMESTAMP(6) NULL)",
                )
            }
        }

        private suspend fun awaitTransactionWaitingForLock() {
            while (transactionsWaitingForLock() == 0L) delay(SLOWER_THAN_INNODB_TRX_CACHE_REFRESH)
        }

        private suspend fun transactionsWaitingForLock(): Long {
            return countRows("SELECT COUNT(*) FROM information_schema.INNODB_TRX WHERE trx_state = 'LOCK WAIT'")
        }

        private suspend fun countRows(query: String): Long {
            return suspendTransaction(db = database) {
                exec(query) { rows ->
                    rows.next()
                    rows.getLong(1)
                } ?: 0L
            }
        }
    }

    private class SutFixture {
        val subjectId = Uuid.random()
        val transactionManager = ExposedTransactionManager(database)
        val clock = TestClock()
        val sut = SubjectKeysImpl(
            dataSource = SubjectKeyDataSource(SubjectKeyTable(KEY_SCHEMA)),
            masterKey = MasterKey.fromBase64(version = 1, encoded = randomBase64Key()),
            random = SecureRandom(),
            clock = clock,
        )

        suspend fun keyRows(): Long {
            return countRows("SELECT COUNT(*) FROM $KEY_SCHEMA.subject_key WHERE $subjectRow")
        }

        suspend fun liveKeyRows(): Long {
            return countRows(
                "SELECT COUNT(*) FROM $KEY_SCHEMA.subject_key WHERE $subjectRow AND wrapped_key IS NOT NULL",
            )
        }

        suspend fun encrypt(plaintext: String): Result<String> {
            return transactionManager.transaction {
                liveEncryptor().encrypt(plaintext)
            }
        }

        suspend fun liveEncryptor(): Encryptor {
            return requireNotNull(sut.encryptor(subjectId)) { "Subject $subjectId has no live key" }
        }

        suspend fun decrypt(ciphertext: String): Decrypted<String> {
            return transactionManager.transaction {
                sut.decryptor(listOf(subjectId)).decrypt(subjectId, ciphertext)
            }.getOrThrow()
        }

        private val subjectRow: String
            get() {
                return "subject_id = UNHEX('${subjectId.toHexString()}')"
            }
    }

    @Test
    fun `should give two first writers of a new subject the same key`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val firstEncrypted = CompletableDeferred<Unit>()
        val commitFirst = CompletableDeferred<Unit>()

        whenn()
        val results = withContext(Dispatchers.IO) {
            val first = async {
                fixture.transactionManager.transaction {
                    fixture.liveEncryptor().encrypt("first").also {
                        firstEncrypted.complete(Unit)
                        commitFirst.await()
                    }
                }
            }
            firstEncrypted.await()
            val second = async { fixture.encrypt("second") }
            awaitTransactionWaitingForLock()
            commitFirst.complete(Unit)
            listOf(first.await(), second.await())
        }

        then()
        assertTrue(results.all { it.isSuccess }) { results.joinToString { it.exceptionOrNull().toString() } }
        assertEquals(1L, fixture.keyRows())
        val decrypted = results.map { (fixture.decrypt(it.getOrThrow()) as Decrypted.Plain).value }
        assertEquals(listOf("first", "second"), decrypted)
    }

    @Test
    fun `should not keep a key created by a transaction that rolled back`() = runIntegrationTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.transactionManager.transaction {
            fixture.liveEncryptor().encrypt("note")
            throw IllegalStateException("the service write failed")
        }

        then()
        assertTrue(result.isFailure)
        assertEquals(0L, fixture.keyRows())
    }

    @Test
    fun `should leave a shredded subject's stored fields unreadable`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val ciphertext = fixture.encrypt("jane@example.com").getOrThrow()

        whenn()
        fixture.transactionManager.transaction { fixture.sut.shred(fixture.subjectId) }.getOrThrow()

        then()
        assertEquals(1L, fixture.keyRows())
        assertEquals(0L, fixture.liveKeyRows())
        assertEquals(Decrypted.Shredded, fixture.decrypt(ciphertext))
    }

    @Test
    fun `should shred several subjects at once and keep an earlier shredding time`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val other = Uuid.random()
        fixture.encrypt("jane@example.com").getOrThrow()
        fixture.transactionManager.transaction { fixture.sut.shred(fixture.subjectId) }.getOrThrow()
        val firstShreddedAt = fixture.clock.now
        fixture.clock += 1.hours

        whenn()
        fixture.transactionManager.transaction { fixture.sut.shredAll(listOf(fixture.subjectId, other)) }.getOrThrow()

        then()
        val shreddedAt = fixture.transactionManager.transaction {
            SubjectKeyDataSource(SubjectKeyTable(KEY_SCHEMA)).getAll(setOf(fixture.subjectId, other))
        }.getOrThrow().associate { it.subjectId to (it as StoredSubjectKey.Shredded).shreddedAt }
        assertEquals(mapOf(fixture.subjectId to firstShreddedAt, other to fixture.clock.now), shreddedAt)
        assertEquals(0L, fixture.liveKeyRows())
    }

    @Test
    fun `should not create a new key for a shredded subject`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.transaction { fixture.sut.shred(fixture.subjectId) }.getOrThrow()

        whenn()
        val encryptor = fixture.transactionManager.transaction { fixture.sut.encryptor(fixture.subjectId) }

        then()
        assertNull(encryptor.getOrThrow())
        assertEquals(0L, fixture.liveKeyRows())
    }
}
