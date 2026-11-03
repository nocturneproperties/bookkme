package com.bookk.business.data.datasource

import com.bookk.business.data.orm.table.ClientTable
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.core.data.database.createDatabase
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.SQLException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ClientRetentionMariaDbTest {

    companion object {
        private const val PASSWORD = "test-password"
        private const val PORT = 3306
        private const val LOCK_WAIT_TIMEOUT = 1205
        private val LONG_AGO = Instant.parse("2000-01-01T00:00:00Z")

        private val container = GenericContainer(DockerImageName.parse("mariadb:11.8.3-ubi9"))
            .withEnv("MARIADB_ROOT_PASSWORD", PASSWORD)
            .withExposedPorts(PORT)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*", 2))

        private lateinit var database: Database
        private lateinit var waiterDatabase: Database

        @JvmStatic
        @BeforeAll
        fun startMariaDb() {
            container.start()
            database = createDatabase(
                schemaName = "business",
                dbUrl = "mariadb://${container.host}",
                dbPort = container.getMappedPort(PORT).toString(),
                dbUsername = "root",
                dbPassword = PASSWORD,
            )
            waiterDatabase = createDatabase(
                schemaName = "business",
                dbUrl = "mariadb://${container.host}",
                dbPort = container.getMappedPort(PORT).toString(),
                dbUsername = "root",
                dbPassword = PASSWORD,
                maxPoolSize = 1,
            )
        }
    }

    @BeforeEach
    fun startFromEmptyTables() {
        transaction(db = database) { exec("DELETE FROM business") }
    }

    private class SutFixture {
        val clients = ClientDataSourceImpl()
        val businesses = BusinessDataSourceImpl()

        suspend fun createBusiness(): Uuid = suspendTransaction(db = database) {
            businesses.createBusiness(Uuid.random(), "Salon", "USD", TimeZone.UTC).id
        }

        suspend fun createClient(businessId: Uuid): Uuid = suspendTransaction(db = database) {
            clients.createDetachedClient(businessId, Client.Detached.stub(phone = null, email = null)).id
        }

        suspend fun backdate(clientId: Uuid, to: Instant) = suspendTransaction(db = database) {
            ClientTable.update(where = { ClientTable.id eq clientId }) { it[lastActivityAt] = to }
        }

        suspend fun lastActivityAt(clientId: Uuid): Instant = suspendTransaction(db = database) {
            ClientTable.select(ClientTable.lastActivityAt).where { ClientTable.id eq clientId }.single()[ClientTable.lastActivityAt]
        }

        suspend fun <T> secondWhileFirstHolds(firstIsolation: Int, first: suspend () -> Unit, second: suspend () -> T): Result<T> {
            val firstHeld = CompletableDeferred<Unit>()
            val secondFinished = CompletableDeferred<Unit>()
            return withContext(Dispatchers.IO) {
                val holder = async {
                    suspendTransaction(db = database, transactionIsolation = firstIsolation) {
                        first()
                        firstHeld.complete(Unit)
                        secondFinished.await()
                    }
                }
                firstHeld.await()
                val outcome = runSuspendCatching {
                    suspendTransaction(db = waiterDatabase) {
                        exec("SET SESSION innodb_lock_wait_timeout = 1")
                        second()
                    }
                }
                secondFinished.complete(Unit)
                holder.await()
                outcome
            }
        }
    }

    @Test
    fun `should move a client's activity forward to a later visit only`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()
        val clientId = fixture.createClient(businessId)
        val laterVisitEnd = Instant.parse("2099-06-01T10:00:00Z")
        suspendTransaction(db = database) { fixture.clients.recordVisit(businessId, clientId, laterVisitEnd) }

        whenn()
        suspendTransaction(db = database) { fixture.clients.recordVisit(businessId, clientId, Instant.parse("2099-01-01T10:00:00Z")) }

        then()
        assertEquals(laterVisitEnd, fixture.lastActivityAt(clientId))
    }

    @Test
    fun `should delete only the business's clients inactive since the cutoff`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()
        val otherBusinessId = fixture.createBusiness()
        val inactive = fixture.createClient(businessId)
        val active = fixture.createClient(businessId)
        val inactiveElsewhere = fixture.createClient(otherBusinessId)
        fixture.backdate(inactive, LONG_AGO)
        fixture.backdate(inactiveElsewhere, LONG_AGO)

        whenn()
        val deleted = suspendTransaction(db = database) { fixture.clients.deleteInactiveClients(businessId, Clock.System.now() - 1.days) }

        then()
        assertEquals(listOf(inactive), deleted)
        assertNull(suspendTransaction(db = database) { fixture.clients.getClientById(businessId, inactive) })
        assertNotNull(suspendTransaction(db = database) { fixture.clients.getClientById(businessId, active) })
        assertNotNull(suspendTransaction(db = database) { fixture.clients.getClientById(otherBusinessId, inactiveElsewhere) })
    }

    @Test
    fun `should not hold up a new client of the business while deleting at read committed`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()
        fixture.backdate(fixture.createClient(businessId), LONG_AGO)

        whenn()
        val creation = fixture.secondWhileFirstHolds(
            firstIsolation = Connection.TRANSACTION_READ_COMMITTED,
            first = { fixture.clients.deleteInactiveClients(businessId, Clock.System.now() - 1.days) },
            second = { fixture.clients.createDetachedClient(businessId, Client.Detached.stub(phone = null, email = null)) },
        )

        then()
        assertTrue(creation.isSuccess)
    }

    @Test
    fun `should hold up a new client of the business when deleting at repeatable read`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()
        fixture.backdate(fixture.createClient(businessId), LONG_AGO)

        whenn()
        val creation = fixture.secondWhileFirstHolds(
            firstIsolation = Connection.TRANSACTION_REPEATABLE_READ,
            first = { fixture.clients.deleteInactiveClients(businessId, Clock.System.now() - 1.days) },
            second = { fixture.clients.createDetachedClient(businessId, Client.Detached.stub(phone = null, email = null)) },
        )

        then()
        val causes = generateSequence(creation.exceptionOrNull()) { it.cause }
        assertTrue(causes.any { it is SQLException && it.errorCode == LOCK_WAIT_TIMEOUT })
    }
}
