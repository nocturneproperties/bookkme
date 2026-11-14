package library.outbox.di

import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.core.data.eventstreaming.StandardEventProducer
import kotlinx.serialization.protobuf.ProtoBuf
import library.outbox.OutboxEventProducer
import library.outbox.OutboxRelay
import library.outbox.datasource.OutboxEventDataSource
import library.outbox.datasource.OutboxEventDataSourceImpl
import org.koin.core.qualifier.Qualifier
import org.koin.dsl.bind
import org.koin.dsl.module

fun outboxModule(qualifier: Qualifier) = module {
    scope(qualifier) {
        scoped<OutboxEventDataSource> { OutboxEventDataSourceImpl() }
        scoped<StandardEventProducer> { OutboxEventProducer(get(), get(), ProtoBuf { encodeDefaults = true }, get()) }
        scoped { OutboxRelay(get(), get(), get(), get()) } bind EventHandler::class
    }
}
