package com.bookk.core.data.eventstreaming.impl.kafka

import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.EventStreaming.RawRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import kotlin.reflect.KType

class KafkaEventProducer internal constructor(
    private val producer: Producer<String, ByteArray>,
    private val protoBuf: ProtoBuf
) : EventStreaming.Producer<String>, EventStreaming.RawProducer<String> {

    constructor(servers: List<String>, client: String, protoBuf: ProtoBuf) : this(
        KafkaProducer(
            mapOf(
                ProducerConfig.CLIENT_ID_CONFIG to client,
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to servers.joinToString { it },
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to "true",
                ProducerConfig.RETRIES_CONFIG to 10,
                ProducerConfig.ACKS_CONFIG to "all"
            ),
            StringSerializer(),
            ByteArraySerializer()
        ),
        protoBuf
    )

    override suspend fun <T : EventStreaming.Event<String>> send(data: T, kType: KType) {
        val encodedData = protoBuf.encodeToByteArray(protoBuf.serializersModule.serializer(kType), data)
        publish(data.topic, data.partitionKey, encodedData)
    }

    suspend fun replay(dltEvent: DltEvent) {
        publish(dltEvent.originalTopic, null, dltEvent.payload)
    }

    override suspend fun publish(topic: String, partitionKey: String?, payload: ByteArray) {
        publishAll(listOf(RawRecord(topic, partitionKey, payload)))
    }

    override suspend fun publishAll(records: List<RawRecord<String>>) {
        records.map(::sendUnacknowledged).awaitAll()
    }

    private fun sendUnacknowledged(record: RawRecord<String>): Deferred<Unit> {
        val acknowledgement = CompletableDeferred<Unit>()
        producer.send(ProducerRecord(record.topic, record.partitionKey, record.payload)) { _, exception ->
            if (exception == null) acknowledgement.complete(Unit) else acknowledgement.completeExceptionally(exception)
        }
        return acknowledgement
    }
}
