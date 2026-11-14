package library.outbox

import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.eventstreaming.DataSubject
import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.impl.PersonalTestEvent
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import library.outbox.datasource.OutboxEventDataSourceImpl
import library.outbox.orm.table.OutboxEventTable
import library.outbox.personal.MarkingPersonalDataCipher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class OutboxEventProducerTest {

    @Serializable
    private data class SampleEvent(
        @ProtoNumber(1) val value: String,
        @ProtoNumber(2) override val idempotencyKey: String,
        @ProtoNumber(3) override val partitionKey: String?,
    ) : EventStreaming.Event<String> {
        @ProtoNumber(4)
        override val topic: String = "sample.topic"
    }

    private class SutFixture {
        val transactionManager = ExposedTransactionManager(createTestDatabase(OutboxEventTable))
        val protoBuf = ProtoBuf { encodeDefaults = true }
        val outboxEventDataSource = OutboxEventDataSourceImpl()
        val cipher = MarkingPersonalDataCipher()
        val sut = OutboxEventProducer(
            outboxEventDataSource = outboxEventDataSource,
            transactionManager = transactionManager,
            protoBuf = protoBuf,
            personalDataCipher = cipher,
        )

        suspend fun pendingEvents() = transactionManager.transaction {
            outboxEventDataSource.lockPending(limit = 10)
        }.getOrThrow()
    }

    @Test
    fun `should store the serialized event under its topic and partition key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = SampleEvent(value = "value", idempotencyKey = "key-1", partitionKey = "partition-1")

        whenn()
        fixture.transactionManager.transaction { fixture.sut.send(event) }

        then()
        val stored = fixture.pendingEvents().single()
        assertEquals("sample.topic", stored.topic)
        assertEquals("partition-1", stored.partitionKey)
        assertEquals(event, fixture.protoBuf.decodeFromByteArray(SampleEvent.serializer(), stored.payload))
    }

    @Test
    fun `should store an event without personal data as plaintext`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = SampleEvent(value = "value", idempotencyKey = "key-1", partitionKey = null)

        whenn()
        fixture.transactionManager.transaction { fixture.sut.send(event) }.getOrThrow()

        then()
        assertFalse(fixture.pendingEvents().single().personalDataEncrypted)
    }

    @Test
    fun `should store a personal event with its whole payload sealed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = PersonalTestEvent(userId = Uuid.random(), name = "Alice", topic = PERSONAL_TOPIC)

        whenn()
        fixture.transactionManager.transaction { fixture.sut.send(event) }.getOrThrow()

        then()
        val stored = fixture.pendingEvents().single()
        val opened = MarkingPersonalDataCipher.plaintextOf(stored.payload)
        assertTrue(stored.personalDataEncrypted)
        assertEquals(event, fixture.protoBuf.decodeFromByteArray(PersonalTestEvent.serializer(), opened))
    }

    @Test
    fun `should refuse a personal event about an erased data subject`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.cipher.erase(DataSubject.User(userId))
        val event = PersonalTestEvent(userId = userId, name = "Alice", topic = PERSONAL_TOPIC)

        whenn()
        val result = fixture.transactionManager.transaction { fixture.sut.send(event) }

        then()
        assertTrue(result.isFailure)
        assertTrue(fixture.pendingEvents().isEmpty())
    }

    @Test
    fun `should fail and store nothing when sent outside a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = SampleEvent(value = "value", idempotencyKey = "key-1", partitionKey = null)

        whenn()
        val result = runCatching { fixture.sut.send(event) }

        then()
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(fixture.pendingEvents().isEmpty())
    }

    @Test
    fun `should not store the event when the surrounding transaction rolls back`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = SampleEvent(value = "value", idempotencyKey = "key-1", partitionKey = null)

        whenn()
        fixture.transactionManager.transaction {
            fixture.sut.send(event)
            throw IllegalStateException("business write failed")
        }

        then()
        assertTrue(fixture.pendingEvents().isEmpty())
    }

    private companion object {
        const val PERSONAL_TOPIC = "personal.topic"
    }
}
