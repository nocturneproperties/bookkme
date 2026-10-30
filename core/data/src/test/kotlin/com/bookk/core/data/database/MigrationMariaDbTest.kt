package com.bookk.core.data.database

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.read.ListAppender
import ch.qos.logback.core.spi.FilterReply
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

internal class MigrationMariaDbTest {

    companion object {
        private const val ROOT_PASSWORD = "test-password"
        private const val PORT = 3306
        private const val DEFAULT_POOL_SIZE = 10

        private val container = GenericContainer(DockerImageName.parse("mariadb:11.8.3-ubi9"))
            .withEnv("MARIADB_ROOT_PASSWORD", ROOT_PASSWORD)
            .withExposedPorts(PORT)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*", 2))

        @JvmStatic
        @BeforeAll
        fun startMariaDb() {
            container.start()
        }
    }

    private class SutFixture(val schemaName: String) {
        val username = "${schemaName}_user"
        val password = "$schemaName-password"
        val warnings = ListAppender<ILoggingEvent>()

        fun grantOnlyOwnSchema(createSchema: Boolean, requireTls: Boolean = false) = asRoot {
            if (createSchema) execute("CREATE DATABASE `$schemaName`")
            execute("CREATE USER '$username'@'%' IDENTIFIED BY '$password'${if (requireTls) " REQUIRE SSL" else ""}")
            execute("GRANT ALL PRIVILEGES ON `$schemaName`.* TO '$username'@'%'")
        }

        fun grantOnlyOwnSchemaWithoutPassword() = asRoot {
            execute("CREATE DATABASE `$schemaName`")
            execute("CREATE USER '$username'@'%'")
            execute("GRANT ALL PRIVILEGES ON `$schemaName`.* TO '$username'@'%'")
        }

        fun migrate(maxPoolSize: Int = DEFAULT_POOL_SIZE, dbPassword: String = password): Database = capturingWarnings {
            createDatabase(
                schemaName = schemaName,
                dbUrl = "mariadb://${container.host}",
                dbPort = container.getMappedPort(PORT).toString(),
                dbUsername = username,
                dbPassword = dbPassword,
                maxPoolSize = maxPoolSize
            )
        }

        fun warningMessages(): List<String> = warnings.list.map { it.formattedMessage }

        private fun <T> capturingWarnings(block: () -> T): T {
            val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
            warnings.addFilter(WarningsOnly())
            warnings.start()
            rootLogger.addAppender(warnings)
            return try {
                block()
            } finally {
                rootLogger.detachAppender(warnings)
                warnings.stop()
            }
        }

        private fun asRoot(statements: Statement.() -> Unit) {
            DriverManager.getConnection(
                "jdbc:mariadb://${container.host}:${container.getMappedPort(PORT)}",
                "root",
                ROOT_PASSWORD
            ).use { connection -> connection.createStatement().use(statements) }
        }
    }

    private class WarningsOnly : Filter<ILoggingEvent>() {
        override fun decide(event: ILoggingEvent) =
            if (event.level.isGreaterOrEqual(Level.WARN)) FilterReply.NEUTRAL else FilterReply.DENY
    }

    @Test
    fun `should migrate an existing schema as a schema-scoped user without logging warnings`() = runIntegrationTest {
        given()
        val fixture = SutFixture(schemaName = "migration_existing")
        fixture.grantOnlyOwnSchema(createSchema = true)

        whenn()
        val database = fixture.migrate()

        then()
        assertEquals(emptyList<String>(), fixture.warningMessages())
        assertEquals(listOf("migration_marker"), database.tableNames())
    }

    @Test
    fun `should create and migrate a missing schema as a schema-scoped user`() = runIntegrationTest {
        given()
        val fixture = SutFixture(schemaName = "migration_fresh")
        fixture.grantOnlyOwnSchema(createSchema = false)

        whenn()
        val database = fixture.migrate()

        then()
        assertEquals(listOf("migration_marker"), database.tableNames())
    }

    @Test
    fun `should migrate and connect over tls as a user that requires it`() = runIntegrationTest {
        given()
        val fixture = SutFixture(schemaName = "migration_tls")
        fixture.grantOnlyOwnSchema(createSchema = true, requireTls = true)

        whenn()
        val database = fixture.migrate()

        then()
        assertTrue(database.sslCipher().isNotEmpty())
    }

    @Test
    fun `should refuse a server certificate it cannot verify`() = runIntegrationTest {
        given()
        val fixture = SutFixture(schemaName = "migration_tls_unverifiable")
        fixture.grantOnlyOwnSchemaWithoutPassword()

        whenn()
        val result = runCatching { fixture.migrate(dbPassword = "") }

        then()
        assertTrue(result.isFailure)
    }

    @Test
    fun `should run consecutive transactions on the same pooled connection`() = runIntegrationTest {
        given()
        val fixture = SutFixture(schemaName = "migration_pool_reuse")
        fixture.grantOnlyOwnSchema(createSchema = true)
        val database = fixture.migrate()

        whenn()
        val connectionIds = List(3) { database.connectionId() }

        then()
        assertEquals(1, connectionIds.toSet().size)
    }

    @Test
    fun `should open no more connections than the pool size for concurrent transactions`() = runIntegrationTest {
        given()
        val fixture = SutFixture(schemaName = "migration_pool_size")
        fixture.grantOnlyOwnSchema(createSchema = true)
        val database = fixture.migrate(maxPoolSize = 2)

        whenn()
        val connectionIds = withContext(Dispatchers.IO) {
            List(6) { async { database.connectionId(holdForSeconds = 0.2) } }.awaitAll()
        }

        then()
        assertTrue(connectionIds.toSet().size <= 2)
    }

    @Test
    fun `should run a default transaction at repeatable read after a read committed one on the same connection`() = runIntegrationTest {
        given()
        val fixture = SutFixture(schemaName = "migration_pool_isolation")
        fixture.grantOnlyOwnSchema(createSchema = true)
        val database = fixture.migrate(maxPoolSize = 1)
        val readCommittedConnectionId = transaction(database, Connection.TRANSACTION_READ_COMMITTED) {
            exec("SELECT CONNECTION_ID()") { result ->
                result.next()
                result.getLong(1)
            }
        }

        whenn()
        val (connectionId, isolation) = transaction(database) {
            exec("SELECT CONNECTION_ID(), @@SESSION.transaction_isolation") { result ->
                result.next()
                result.getLong(1) to result.getString(2)
            } ?: error("isolation query returned no row")
        }

        then()
        assertEquals(readCommittedConnectionId, connectionId)
        assertEquals("REPEATABLE-READ", isolation)
    }

    private fun Database.connectionId(holdForSeconds: Double = 0.0): Long = transaction(this) {
        exec("SELECT CONNECTION_ID(), SLEEP($holdForSeconds)") { result ->
            result.next()
            result.getLong(1)
        } ?: error("CONNECTION_ID() returned no row")
    }

    private fun Database.sslCipher(): String = transaction(this) {
        exec("SHOW SESSION STATUS LIKE 'Ssl_cipher'") { result ->
            result.next()
            result.getString(2)
        } ?: error("Ssl_cipher returned no row")
    }

    private fun Database.tableNames(): List<String> = transaction(this) {
        exec("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'") { result ->
            generateSequence { if (result.next()) result.getString(1) else null }.toList()
        }.orEmpty()
    }
}
