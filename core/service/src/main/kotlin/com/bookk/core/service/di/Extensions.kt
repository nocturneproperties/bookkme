package com.bookk.core.service.di

import com.bookk.core.data.eventstreaming.EventIdempotencyStorage
import com.bookk.core.data.repository.CacheEventIdempotencyStorage
import com.bookk.core.data.repository.CacheIdempotentResponseRepository
import library.idempotency.IdempotentResponseRepository
import org.koin.core.module.Module
import org.koin.dsl.module

internal fun commonModule(): Module = module {
    single<IdempotentResponseRepository> { CacheIdempotentResponseRepository(get()) }
    single<EventIdempotencyStorage> { CacheEventIdempotencyStorage(get()) }
}
