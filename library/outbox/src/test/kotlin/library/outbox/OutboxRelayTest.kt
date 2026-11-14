package library.outbox

import com.bookk.core.data.eventstreaming.DataSubject
import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.EventStreaming.RawRecord
import com.bookk.core.data.eventstreaming.impl.PersonalTestEvent
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.protobuf.ProtoBuf
import library.outbox.datasource.OutboxEvent
import library.outbox.datasource.OutboxEventDataSource
import library.outbox.personal.MarkingPersonalDataCipher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

internal class OutboxRelayTest {

    private class SutFixture {
        val outboxEventDataSource = mockk<OutboxEventDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val rawProducer = mockk<EventStreaming.RawProducer<String>>()
        val batchSize = 2
        val idleDelay = 1.seconds
        val protoBuf = ProtoBuf { encodeDefaults = true }
        val cipher = MarkingPersonalDataCipher()
        val sut = OutboxRelay(
            outboxEventDataSource = outboxEventDataSource,
            transactionManager = transactionManager,
            rawProducer = rawProducer,
            personalDataCipher = cipher,
            batchSize = batchSize,
            idleDelay = idleDelay,
        )
        val published = mutableListOf<RawRecord<String>>()

        init {
            transactionManager.mockTransaction()
            coEvery { rawProducer.publishAll(any()) } coAnswers { published += firstArg<List<RawRecord<String>>>() }
            coEvery { outboxEventDataSource.delete(any()) } just runs
        }

        fun plaintextOf(event: PersonalTestEvent): ByteArray {
            return protoBuf.encodeToByteArray(PersonalTestEvent.serializer(), event)
        }

        fun sealedRow(event: PersonalTestEvent) = OutboxEvent.stub(
            id = 1,
            topic = event.topic,
            partitionKey = event.partitionKey,
            payload = MarkingPersonalDataCipher.sealed(event.dataSubject, plaintextOf(event)),
            personalDataEncrypted = true,
        )

        fun plaintextRow(event: PersonalTestEvent) = OutboxEvent.stub(
            id = 1,
            topic = event.topic,
            partitionKey = event.partitionKey,
            payload = plaintextOf(event),
            personalDataEncrypted = false,
        )

        fun publishedPersonalEvents() = published.map {
            protoBuf.decodeFromByteArray(PersonalTestEvent.serializer(), it.payload)
        }

        fun OutboxEvent.isPublished() = assertEquals(
            1,
            published.count {
                it.topic == topic && it.partitionKey == partitionKey && it.payload.contentEquals(payload)
            },
        )
    }

    @Test
    fun `should publish every locked event in order and then delete them`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = OutboxEvent.stub(id = 1, topic = "topic.first", partitionKey = "key", payload = byteArrayOf(1))
        val second = OutboxEvent.stub(id = 2, topic = "topic.second", partitionKey = null, payload = byteArrayOf(2))
        coEvery { fixture.outboxEventDataSource.lockPending(fixture.batchSize) } returns listOf(first, second)

        whenn()
        val result = fixture.sut.relayBatch()

        then()
        assertEquals(2, result.getOrNull())
        assertEquals(
            listOf("topic.first" to "key", "topic.second" to null),
            fixture.published.map {
                it.topic to
                    it.partitionKey
            },
        )
        assertEquals(listOf(first.payload, second.payload), fixture.published.map { it.payload })
        coVerifyOrder {
            fixture.rawProducer.publishAll(any())
            fixture.outboxEventDataSource.delete(listOf(1L, 2L))
        }
    }

    @Test
    fun `should publish nothing when the outbox is empty`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.outboxEventDataSource.lockPending(fixture.batchSize) } returns emptyList()

        whenn()
        val result = fixture.sut.relayBatch()

        then()
        assertEquals(0, result.getOrNull())
        assertTrue(fixture.published.isEmpty())
    }

    @Test
    fun `should publish a sealed event opened`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = PersonalTestEvent(userId = Uuid.random(), name = "Alice", topic = PERSONAL_TOPIC)
        val row = fixture.sealedRow(event)
        coEvery { fixture.outboxEventDataSource.lockPending(fixture.batchSize) } returns listOf(row)

        whenn()
        fixture.sut.relayBatch()

        then()
        assertEquals(listOf(event), fixture.publishedPersonalEvents())
        coVerify(exactly = 1) { fixture.outboxEventDataSource.delete(listOf(row.id)) }
    }

    @Test
    fun `should publish a plaintext event as it was stored`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = PersonalTestEvent(userId = Uuid.random(), name = "Alice", topic = PERSONAL_TOPIC)
        coEvery {
            fixture.outboxEventDataSource.lockPending(fixture.batchSize)
        } returns listOf(fixture.plaintextRow(event))

        whenn()
        fixture.sut.relayBatch()

        then()
        assertEquals(listOf(event), fixture.publishedPersonalEvents())
    }

    @Test
    fun `should drop a sealed event whose data subject was erased since it was stored`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val row = fixture.sealedRow(PersonalTestEvent(userId = userId, name = "Alice", topic = PERSONAL_TOPIC))
        fixture.cipher.erase(DataSubject.User(userId))
        coEvery { fixture.outboxEventDataSource.lockPending(fixture.batchSize) } returns listOf(row)

        whenn()
        val result = fixture.sut.relayBatch()

        then()
        assertEquals(1, result.getOrNull())
        assertTrue(fixture.published.isEmpty())
        coVerify(exactly = 1) { fixture.outboxEventDataSource.delete(listOf(row.id)) }
    }

    @Test
    fun `should drop a sealed event that cannot be opened and relay the rest of the batch`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val broken = OutboxEvent.stub(
            id = 1,
            topic = PERSONAL_TOPIC,
            payload = byteArrayOf(0x7f, 0x7f, 0x7f),
            personalDataEncrypted = true,
        )
        val plain = OutboxEvent.stub(id = 2, topic = "topic.plain", payload = byteArrayOf(2))
        coEvery { fixture.outboxEventDataSource.lockPending(fixture.batchSize) } returns listOf(broken, plain)

        whenn()
        val result = fixture.sut.relayBatch()

        then()
        assertEquals(2, result.getOrNull())
        assertEquals(listOf("topic.plain"), fixture.published.map { it.topic })
        coVerify(exactly = 1) { fixture.outboxEventDataSource.delete(listOf(1L, 2L)) }
    }

    @Test
    fun `should fail and keep the batch in the outbox when publishing an event fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = OutboxEvent.stub(id = 1, topic = "topic.first")
        val second = OutboxEvent.stub(id = 2, topic = "topic.second")
        coEvery { fixture.outboxEventDataSource.lockPending(fixture.batchSize) } returns listOf(first, second)
        coEvery { fixture.rawProducer.publishAll(any()) } throws IllegalStateException("broker down")

        whenn()
        val result = fixture.sut.relayBatch()

        then()
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { fixture.outboxEventDataSource.delete(any()) }
    }

    @Test
    fun `should relay the next batch immediately while the previous batch was full`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = OutboxEvent.stub(id = 1, topic = "topic.first")
        val second = OutboxEvent.stub(id = 2, topic = "topic.second")
        val third = OutboxEvent.stub(id = 3, topic = "topic.third")
        coEvery { fixture.outboxEventDataSource.lockPending(fixture.batchSize) } returnsMany listOf(
            listOf(first, second),
            listOf(third),
            emptyList(),
        )

        whenn()
        fixture.sut.start(backgroundScope)
        runCurrent()

        then()
        with(fixture) {
            first.isPublished()
            second.isPublished()
            third.isPublished()
        }
        coVerify(exactly = 2) { fixture.outboxEventDataSource.lockPending(fixture.batchSize) }
    }

    @Test
    fun `should wait for the idle delay before polling again after a partial batch`() = runUnitTest {
        given()
        val fixture = SutFixture()
        var polls = 0
        coEvery { fixture.outboxEventDataSource.lockPending(fixture.batchSize) } coAnswers {
            polls++
            emptyList()
        }
        fixture.sut.start(backgroundScope)
        runCurrent()

        whenn()
        advanceTimeBy(fixture.idleDelay - 1.milliseconds)
        runCurrent()
        val pollsJustBeforeIdleDelay = polls
        advanceTimeBy(1.milliseconds)
        runCurrent()

        then()
        assertEquals(1, pollsJustBeforeIdleDelay)
        assertEquals(2, polls)
    }

    @Test
    fun `should keep relaying after a batch fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = OutboxEvent.stub(id = 1, topic = "topic.first")
        coEvery { fixture.outboxEventDataSource.lockPending(fixture.batchSize) } throws
            IllegalStateException("database down") andThen listOf(event) andThen emptyList()

        whenn()
        fixture.sut.start(backgroundScope)
        runCurrent()
        advanceTimeBy(fixture.idleDelay)
        runCurrent()

        then()
        with(fixture) { event.isPublished() }
    }

    private companion object {
        const val PERSONAL_TOPIC = "personal.topic"
    }
}
