package library.encryption.impl.test

import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.database.createDatabase
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.encryption.SubjectKeys
import library.encryption.impl.di.subjectKeysModule
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ShortRetentionSchemaContract(
    private val serviceSchema: String,
    private val shortRetentionSchema: String,
    private val serviceTables: Set<String>,
) {

    private val container = GenericContainer(DockerImageName.parse("mariadb:11.8.3-ubi9"))
        .withEnv("MARIADB_ROOT_PASSWORD", PASSWORD)
        .withExposedPorts(PORT)
        .waitingFor(Wait.forLogMessage(".*ready for connections.*", 2))

    private lateinit var database: Database

    @BeforeAll
    fun migrateServiceSchema() {
        container.start()
        database = createDatabase(
            schemaName = serviceSchema,
            dbUrl = "mariadb://${container.host}",
            dbPort = container.getMappedPort(PORT).toString(),
            dbUsername = "root",
            dbPassword = PASSWORD,
        )
    }

    @AfterAll
    fun stopMariaDb() {
        container.stop()
    }

    private inner class SutFixture {
        private val environment = mapOf(MASTER_KEY to randomBase64Key(), BLIND_INDEX_KEY to randomBase64Key())
        private val koin = koinApplication {
            modules(subjectKeysModule(SCOPE, shortRetentionSchema, environment::get))
        }.koin
        val subjectId = Uuid.random()
        val transactionManager = ExposedTransactionManager(database)
        val sut = koin.createScope("keys", SCOPE).get<SubjectKeys>()

        suspend fun encrypt(plaintext: String): String {
            return transactionManager.transactionOrThrow { sut.encryptor(subjectId).encrypt(plaintext) }
        }

        suspend fun decrypt(ciphertext: String): String? {
            return transactionManager.transactionOrThrow {
                sut.decryptor(listOf(subjectId)).decrypt(subjectId, ciphertext)
            }
        }
    }

    @Test
    fun `should keep only subject keys in the short-retention schema`() = runIntegrationTest {
        given()
        val query = "SELECT table_name FROM information_schema.tables WHERE table_schema = '$shortRetentionSchema'"

        whenn()
        val tables = suspendTransaction(db = database) {
            exec(query) { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
        }

        then()
        assertEquals(listOf("subject_key"), tables)
    }

    @Test
    fun `should keep the outbox, parked events and exports in the service schema`() = runIntegrationTest {
        given()
        val names = serviceTables.joinToString { "'$it'" }
        val query = "SELECT table_name, table_schema FROM information_schema.tables WHERE table_name IN ($names)"

        whenn()
        val schemasByTable = suspendTransaction(db = database) {
            exec(query) { rows -> buildList { while (rows.next()) add(rows.getString(1) to rows.getString(2)) } }
        }.orEmpty().groupBy({ it.first }, { it.second })

        then()
        assertEquals(serviceTables.associateWith { listOf(serviceSchema) }, schemasByTable)
    }

    @Test
    fun `should read back a field encrypted with a key from the short-retention schema`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val ciphertext = fixture.encrypt("jane@example.com")

        whenn()
        val plaintext = fixture.decrypt(ciphertext)

        then()
        assertEquals("jane@example.com", plaintext)
    }

    @Test
    fun `should leave a field unreadable once its subject's key is shredded`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val ciphertext = fixture.encrypt("jane@example.com")

        whenn()
        fixture.transactionManager.transactionOrThrow { fixture.sut.shred(fixture.subjectId) }

        then()
        assertNull(fixture.decrypt(ciphertext))
    }

    private companion object {
        const val PASSWORD = "test-password"
        const val PORT = 3306
        const val MASTER_KEY = "APPLICATION_SUBJECT_MASTER_KEY"
        const val BLIND_INDEX_KEY = "APPLICATION_BLIND_INDEX_KEY"
        val SCOPE = named("ShortRetentionSchemaContract")

        fun randomBase64Key(): String {
            return Base64.encode(ByteArray(32).also(SecureRandom()::nextBytes))
        }
    }
}
