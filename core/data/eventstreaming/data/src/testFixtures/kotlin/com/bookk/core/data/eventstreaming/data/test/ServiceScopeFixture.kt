package com.bookk.core.data.eventstreaming.data.test

import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.StandardEventConsumer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import io.mockk.mockk
import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier
import org.koin.core.scope.Scope
import org.koin.dsl.koinApplication
import org.koin.dsl.module

fun serviceScopeWithMockedInfrastructure(qualifier: Qualifier, serviceModule: Module): Scope {
    val infrastructure = module {
        single<CacheClient<String>> { mockk() }
        scope(qualifier) {
            scoped<TransactionManager> { mockk() }
            scoped<EventStreaming.RawProducer<String>> { mockk() }
            scoped<StandardEventConsumer> { mockk(relaxed = true) }
        }
    }
    return koinApplication { modules(serviceModule, infrastructure) }
        .koin
        .createScope("service-scope-test", qualifier)
}
