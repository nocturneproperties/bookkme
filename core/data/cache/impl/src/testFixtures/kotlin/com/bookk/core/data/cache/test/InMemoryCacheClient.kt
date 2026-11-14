package com.bookk.core.data.cache.test

import com.bookk.core.data.cache.CacheClient
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.github.benmanes.caffeine.cache.Ticker
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

class InMemoryCacheClient(
    private val protobuf: ProtoBuf = ProtoBuf { encodeDefaults = true },
    timeSource: TimeSource = TimeSource.Monotonic,
) : CacheClient<String> {

    private class InMemCacheEntry(val value: ByteArray, val ttl: Duration, val keepsExpiration: Boolean = false)

    private class WritingExpiry : Expiry<String, InMemCacheEntry> {
        override fun expireAfterCreate(key: String, value: InMemCacheEntry, currentTime: Long): Long {
            return value.ttl.inWholeNanoseconds
        }

        override fun expireAfterUpdate(
            key: String,
            value: InMemCacheEntry,
            currentTime: Long,
            currentDuration: Long,
        ): Long = if (value.keepsExpiration) currentDuration else value.ttl.inWholeNanoseconds

        override fun expireAfterRead(
            key: String,
            value: InMemCacheEntry,
            currentTime: Long,
            currentDuration: Long,
        ): Long = currentDuration
    }

    private val createdAt = timeSource.markNow()

    private val cache = Caffeine<String, InMemCacheEntry>.newBuilder()
        .ticker(Ticker { createdAt.elapsedNow().inWholeNanoseconds })
        .expireAfter(WritingExpiry())
        .maximumSize(10_000)
        .build<String, InMemCacheEntry>()

    override suspend fun <V : Any> set(
        key: String,
        value: V,
        kType: KType,
        expiration: Duration?,
    ) {
        val serializer = protobuf.serializersModule.serializer(kType)
        val data = protobuf.encodeToByteArray(serializer, value)
        cache.put(key, InMemCacheEntry(data, expiration ?: 5.minutes))
    }

    override suspend fun <V : Any> setIfAbsent(
        key: String,
        value: V,
        kType: KType,
        expiration: Duration?,
    ): Boolean {
        val serializer = protobuf.serializersModule.serializer(kType)
        val data = protobuf.encodeToByteArray(serializer, value)
        return cache.asMap().putIfAbsent(key, InMemCacheEntry(data, expiration ?: 5.minutes)) == null
    }

    override suspend fun <V : Any> get(key: String, kType: KType): V? {
        val serializer = protobuf.serializersModule.serializer(kType)
        return cache.getIfPresent(key)?.let {
            protobuf.decodeFromByteArray(serializer, it.value) as V
        }
    }

    override suspend fun withTransaction(action: suspend CacheClient<String>.() -> Unit) {
        this.action()
    }

    override suspend fun delete(key: String) {
        cache.invalidate(key)
    }

    override suspend fun <V : Any> deleteIfEquals(key: String, expected: V, kType: KType) {
        val serializer = protobuf.serializersModule.serializer(kType)
        val data = protobuf.encodeToByteArray(serializer, expected)
        val entry = cache.getIfPresent(key) ?: return
        if (entry.value.contentEquals(data)) cache.asMap().remove(key, entry)
    }

    override suspend fun increment(key: String, expiration: Duration): Long {
        val entry = cache.asMap().compute(key) { _, current ->
            current?.let {
                InMemCacheEntry(it.count().plus(1).toString().toByteArray(), it.ttl, keepsExpiration = true)
            }
                ?: InMemCacheEntry("1".toByteArray(), expiration)
        }
        return requireNotNull(entry).count()
    }

    private fun InMemCacheEntry.count(): Long = value.decodeToString().toLong()

    override fun close() {
    }
}
