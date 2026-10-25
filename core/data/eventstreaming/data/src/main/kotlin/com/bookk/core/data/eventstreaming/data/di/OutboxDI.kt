package com.bookk.core.data.eventstreaming.data.di

import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.core.data.eventstreaming.OutboxEventDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.data.datasource.OutboxEventDataSourceImpl
import com.bookk.core.data.eventstreaming.data.outbox.OutboxEventProducer
import com.bookk.core.data.eventstreaming.data.outbox.OutboxRelay
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.core.qualifier.Qualifier
import org.koin.dsl.bind
import org.koin.dsl.module

fun outboxModule(qualifier: Qualifier) = module {
    scope(qualifier) {
        scoped<OutboxEventDataSource> { OutboxEventDataSourceImpl() }
        scoped<StandardEventProducer> { OutboxEventProducer(get(), get(), ProtoBuf { encodeDefaults = true }) }
        scoped { OutboxRelay(get(), get(), get()) } bind EventHandler::class
    }
}
