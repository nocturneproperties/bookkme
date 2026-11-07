package library.export.impl

import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.database.createDatabase
import com.bookk.core.test.TestClock
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import library.export.DataExportStatus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

internal class DataExportStartMariaDbTest {

    companion object {
        private val SLOWER_THAN_INNODB_TRX_CACHE_REFRESH = 150.milliseconds
        private const val PASSWORD = "test-password"
        private const val PORT = 3306

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
                schemaName = "export_test",
                dbUrl = "mariadb://${container.host}",
                dbPort = container.getMappedPort(PORT).toString(),
                dbUsername = "root",
                dbPassword = PASSWORD,
            )
        }

        private suspend fun awaitTransactionWaitingForLock() {
            while (transactionsWaitingForLock() == 0L) delay(SLOWER_THAN_INNODB_TRX_CACHE_REFRESH)
        }

        private suspend fun transactionsWaitingForLock(): Long = suspendTransaction(db = database) {
            exec("SELECT COUNT(*) FROM information_schema.INNODB_TRX WHERE trx_state = 'LOCK WAIT'") { rows ->
                rows.next()
                rows.getLong(1)
            } ?: 0L
        }
    }

    private class SutFixture {
        val ownerId = Uuid.random()
        val dataSource = spyk(DataExportDataSource())
        val transactionManager = ExposedTransactionManager(database)
        val sut = DataExportsImpl(dataSource, TestClock())
        val firstLockTaken = CompletableDeferred<Unit>()
        val resumeFirst = CompletableDeferred<Unit>()
        private val lockReads = AtomicInteger()

        init {
            coEvery { dataSource.getForUpdate(ownerId) } coAnswers {
                val found = callOriginal()
                if (lockReads.getAndIncrement() == 0) {
                    firstLockTaken.complete(Unit)
                    resumeFirst.await()
                }
                found
            }
        }

        suspend fun start() = transactionManager.transaction { sut.start(ownerId) }

        suspend fun rows(): Long = suspendTransaction(db = database) {
            exec("SELECT COUNT(*) FROM data_export WHERE owner_id = UNHEX('${ownerId.toHexString()}')") { rows ->
                rows.next()
                rows.getLong(1)
            } ?: 0L
        }
    }

    @Test
    fun `should give both of two first exports started at the same moment the one queued export`() = runIntegrationTest {
        given()
        val fixture = SutFixture()

        whenn()
        val results = withContext(Dispatchers.IO) {
            val first = async { fixture.start() }
            fixture.firstLockTaken.await()
            val second = async { fixture.start() }
            awaitTransactionWaitingForLock()
            fixture.resumeFirst.complete(Unit)
            listOf(first.await(), second.await())
        }

        then()
        assertTrue(results.all { it.isSuccess }) { results.joinToString { it.exceptionOrNull().toString() } }
        assertEquals(listOf(DataExportStatus.RUNNING, DataExportStatus.RUNNING), results.map { it.getOrThrow().status })
        assertEquals(1, results.map { it.getOrThrow().requestedAt }.distinct().size)
        assertEquals(1L, fixture.rows())
    }
}
