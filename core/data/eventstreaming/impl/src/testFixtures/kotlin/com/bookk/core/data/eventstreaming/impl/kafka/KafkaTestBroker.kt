package com.bookk.core.data.eventstreaming.impl.kafka

import com.bookk.core.data.eventstreaming.EventStreaming
import kotlinx.serialization.protobuf.ProtoBuf
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import kotlin.uuid.Uuid

object KafkaTestBroker {
    private val container = ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))

    val servers: List<String> by lazy {
        container.start()
        listOf(container.bootstrapServers)
    }

    val protoBuf = ProtoBuf { encodeDefaults = true }

    fun createTopic(name: String, partitions: Int) {
        Admin.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to servers.single())).use { admin ->
            if (admin.listTopics().names().get().contains(name)) return
            admin.createTopics(listOf(NewTopic(name, partitions, 1))).all().get()
        }
    }

    fun drain(topic: String, expected: Int): List<ConsumerRecord<String, ByteArray>> =
        awaitRecords(topic, expected) { true }

    fun awaitRecords(
        topic: String,
        expected: Int,
        matching: (ConsumerRecord<String, ByteArray>) -> Boolean
    ): List<ConsumerRecord<String, ByteArray>> {
        val consumer = KafkaConsumer(
            mapOf<String, Any>(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to servers.single(),
                ConsumerConfig.GROUP_ID_CONFIG to "drain-${Uuid.random()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest"
            ),
            StringDeserializer(),
            ByteArrayDeserializer()
        )
        consumer.use {
            it.subscribe(listOf(topic))
            val collected = mutableListOf<ConsumerRecord<String, ByteArray>>()
            val deadline = System.currentTimeMillis() + 10_000
            while (collected.size < expected && System.currentTimeMillis() < deadline) {
                it.poll(Duration.ofMillis(500)).forEach { record -> if (matching(record)) collected += record }
            }
            return collected
        }
    }
}

class NoopProducer : EventStreaming.Producer<String> {
    override suspend fun <T : EventStreaming.Event<String>> send(data: T, kType: kotlin.reflect.KType) = Unit
}
