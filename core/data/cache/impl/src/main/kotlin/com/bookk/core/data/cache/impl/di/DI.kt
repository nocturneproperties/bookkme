package com.bookk.core.data.cache.impl.di

import com.bookk.core.AppLevelConstants
import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.impl.RedisCacheClient
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.dsl.module

fun cacheModule() = module {
    single<CacheClient<String>> {
        RedisCacheClient(
            host = AppLevelConstants.cacheHost,
            port = AppLevelConstants.cachePort.toInt(),
            protobuf = ProtoBuf { encodeDefaults = true },
            password = AppLevelConstants.cachePass
        )
    }
}