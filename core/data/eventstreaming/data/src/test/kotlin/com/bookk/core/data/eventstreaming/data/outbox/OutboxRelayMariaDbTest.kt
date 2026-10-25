package com.bookk.core.data.eventstreaming.data.outbox

import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.database.createDatabase
import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.data.datasource.OutboxEventDataSourceImpl
import com.bookk.core.data.eventstreaming.data.orm.table.OutboxEventTable
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

internal class OutboxRelayMariaDbTest {

    companion object {
        private val SLOWER_THAN_INNODB_TRX_CACHE_REFRESH = 150.milliseconds
        private const val PASSWORD = "test-password"
        private const val PORT = 3306

        private val container = GenericContainer(DockerImageName.parse("mariadb:11.8.3-ubi9"))
            .withEnv("MARIADB_ROOT_PASSWORD", PASSWORD)
            .withExposedPorts(PORT)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*", 2))

        @JvmStatic
        @BeforeAll
        fun startMariaDb() {
            container.start()
        }
    }

    private class RecordingProducer(private val beforePublish: suspend () -> Unit = {}) : EventStreaming.RawProducer<String> {
        val publishedTopics = ConcurrentLinkedQueue<String>()

        override suspend fun publish(topic: String, partitionKey: String?, payload: ByteArray) {
            beforePublish()
            publishedTopics += topic
        }
    }

    private class SutFixture {
        val database: Database = createDatabase(
            schemaName = "outbox_${Uuid.random().toHexString()}",
            dbUrl = "mariadb://${container.host}",
            dbPort = container.getMappedPort(PORT).toString(),
            dbUsername = "root",
            dbPassword = PASSWORD
        )
        val transactionManager = ExposedTransactionManager(database)
        val outboxEventDataSource = OutboxEventDataSourceImpl()

        init {
            transaction(database) { SchemaUtils.create(OutboxEventTable) }
        }

        fun relay(rawProducer: EventStreaming.RawProducer<String>) =
            OutboxRelay(outboxEventDataSource, transactionManager, rawProducer)

        suspend fun enqueue(vararg topics: String) = topics.forEach { topic ->
            suspendTransaction(db = database) {
                outboxEventDataSource.enqueue(topic = topic, partitionKey = null, payload = byteArrayOf(1))
            }
        }

        suspend fun enqueueFailingFastOnLockWait(topic: String) = suspendTransaction(db = database) {
            exec("SET SESSION innodb_lock_wait_timeout = 1")
            outboxEventDataSource.enqueue(topic = topic, partitionKey = null, payload = byteArrayOf(1))
        }

        suspend fun remainingEvents(): Long = suspendTransaction(db = database) { OutboxEventTable.selectAll().count() }

        suspend fun awaitTransactionWaitingForLock() {
            while (transactionsWaitingForLock() == 0L) delay(SLOWER_THAN_INNODB_TRX_CACHE_REFRESH)
        }

        private suspend fun transactionsWaitingForLock(): Long = suspendTransaction(db = database) {
            exec("SELECT COUNT(*) FROM information_schema.INNODB_TRX WHERE trx_state = 'LOCK WAIT'") { rows ->
                rows.next()
                rows.getLong(1)
            } ?: 0L
        }
    }

    @Test
    fun `should make a second relay wait for the first relay's batch and publish every event exactly once`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        fixture.enqueue("topic.first", "topic.second", "topic.third")
        val holderPublishing = CompletableDeferred<Unit>()
        val waiterBlocked = CompletableDeferred<Unit>()
        val holderProducer = RecordingProducer {
            holderPublishing.complete(Unit)
            waiterBlocked.await()
        }
        val waiterProducer = RecordingProducer()

        whenn()
        val (holderResult, waiterResult) = withContext(Dispatchers.IO) {
            val holder = async { fixture.relay(holderProducer).relayBatch() }
            holderPublishing.await()
            val waiter = async { fixture.relay(waiterProducer).relayBatch() }
            fixture.awaitTransactionWaitingForLock()
            waiterBlocked.complete(Unit)
            holder.await() to waiter.await()
        }

        then()
        assertEquals(3, holderResult.getOrNull())
        assertEquals(0, waiterResult.getOrNull())
        assertEquals(listOf("topic.first", "topic.second", "topic.third"), holderProducer.publishedTopics.toList())
        assertTrue(waiterProducer.publishedTopics.isEmpty())
        assertEquals(0, fixture.remainingEvents())
    }

    @Test
    fun `should accept a new outbox event while a relay is publishing its batch`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        fixture.enqueue("topic.first", "topic.second")
        val holderPublishing = CompletableDeferred<Unit>()
        val enqueueFinished = CompletableDeferred<Unit>()
        val holderProducer = RecordingProducer {
            holderPublishing.complete(Unit)
            enqueueFinished.await()
        }

        whenn()
        val enqueueResult = withContext(Dispatchers.IO) {
            val holder = async { fixture.relay(holderProducer).relayBatch() }
            holderPublishing.await()
            val result = runCatching { fixture.enqueueFailingFastOnLockWait("topic.during-relay") }
            enqueueFinished.complete(Unit)
            holder.await()
            result
        }

        then()
        assertTrue(enqueueResult.isSuccess, "enqueue failed with ${enqueueResult.exceptionOrNull()}")
        assertEquals(1, fixture.remainingEvents())
    }

    @Test
    fun `should leave the batch in the outbox for the next relay when publishing fails`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        fixture.enqueue("topic.first", "topic.second")
        val failingProducer = RecordingProducer { throw IllegalStateException("broker down") }
        val recoveredProducer = RecordingProducer()

        whenn()
        val failedResult = withContext(Dispatchers.IO) { fixture.relay(failingProducer).relayBatch() }
        val recoveredResult = withContext(Dispatchers.IO) { fixture.relay(recoveredProducer).relayBatch() }

        then()
        assertTrue(failedResult.isFailure)
        assertEquals(2, recoveredResult.getOrNull())
        assertEquals(listOf("topic.first", "topic.second"), recoveredProducer.publishedTopics.toList())
        assertEquals(0, fixture.remainingEvents())
    }
}
