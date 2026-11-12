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
                        "wrapped_key VARBINARY(60) NOT NULL, " +
                        "master_key_version INT NOT NULL, " +
                        "created_at TIMESTAMP(6) NOT NULL)",
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
        val sut = SubjectKeysImpl(
            dataSource = SubjectKeyDataSource(SubjectKeyTable(KEY_SCHEMA)),
            masterKey = MasterKey.fromBase64(version = 1, encoded = randomBase64Key()),
            random = SecureRandom(),
            clock = TestClock(),
        )

        suspend fun keyRows(): Long {
            val subjectHex = subjectId.toHexString()
            return countRows("SELECT COUNT(*) FROM $KEY_SCHEMA.subject_key WHERE subject_id = UNHEX('$subjectHex')")
        }

        suspend fun encrypt(plaintext: String): Result<String> {
            return transactionManager.transaction {
                sut.encryptor(subjectId).encrypt(plaintext)
            }
        }

        suspend fun decrypt(ciphertext: String): String? {
            return transactionManager.transaction {
                sut.decryptor(listOf(subjectId)).decrypt(subjectId, ciphertext)
            }.getOrThrow()
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
                    fixture.sut.encryptor(fixture.subjectId).encrypt("first").also {
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
        assertEquals(listOf("first", "second"), results.map { fixture.decrypt(it.getOrThrow()) })
    }

    @Test
    fun `should not keep a key created by a transaction that rolled back`() = runIntegrationTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.transactionManager.transaction {
            fixture.sut.encryptor(fixture.subjectId).encrypt("note")
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
        assertEquals(0L, fixture.keyRows())
        assertNull(fixture.decrypt(ciphertext))
    }
}
