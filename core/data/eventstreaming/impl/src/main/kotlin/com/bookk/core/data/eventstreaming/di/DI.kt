package com.bookk.core.data.eventstreaming.di

import com.bookk.core.AppLevelConstants
import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.StandardEventConsumer
import com.bookk.core.data.eventstreaming.impl.embedded.EmbeddedEventConsumer
import com.bookk.core.data.eventstreaming.impl.embedded.EmbeddedEventProducer
import com.bookk.core.data.eventstreaming.impl.embedded.TopicQueueHolder
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaEventConsumer
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaEventProducer
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.core.qualifier.Qualifier
import org.koin.dsl.bind
import org.koin.dsl.module

fun eventStreamingTransportModule(qualifier: Qualifier, serviceName: String) = module {
    scope(qualifier) {
        scoped<StandardEventConsumer> {
            val servers = AppLevelConstants.eventStreamingHost.split(',')
            KafkaEventConsumer(servers, consumerGroup(serviceName), get(), ProtoBuf { encodeDefaults = true }, get<KafkaEventProducer>(), get(), get())
        }
        scoped {
            val servers = AppLevelConstants.eventStreamingHost.split(',')
            val clientName = "${serviceName}_producer"
            KafkaEventProducer(servers, clientName, ProtoBuf { encodeDefaults = true })
        } bind EventStreaming.RawProducer::class
    }
}

fun topicQueueHolderModule() = module {
    single { TopicQueueHolder<String>() }
}

fun embeddedEventStreamingTransportModule(qualifier: Qualifier, serviceName: String) = module {
    scope(qualifier) {
        scoped<StandardEventConsumer> {
            EmbeddedEventConsumer(get(), ProtoBuf { encodeDefaults = true }, get(), get(), get(), consumerGroup(serviceName))
        }
        scoped { EmbeddedEventProducer(get()) } bind EventStreaming.RawProducer::class
    }
}

private fun consumerGroup(serviceName: String) = "${serviceName}_group"
