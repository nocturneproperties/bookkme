package com.bookk.core.data.cache

import java.io.Closeable
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Duration

interface CacheClient<K> : Closeable {
    suspend fun <V : Any> set(key: K, value: V, kType: KType, expiration: Duration?)
    suspend fun <V : Any> setIfAbsent(key: K, value: V, kType: KType, expiration: Duration?): Boolean
    suspend fun <V : Any> get(key: K, kType: KType): V?
    suspend fun withTransaction(action: suspend CacheClient<K>.() -> Unit)
    suspend fun delete(key: K)
    suspend fun <V : Any> deleteIfEquals(key: K, expected: V, kType: KType)
}

suspend inline fun <K, reified V : Any> CacheClient<K>.set(key: K, value: V, ttl: Duration? = null) {
    set(key, value, typeOf<V>(), ttl)
}

suspend inline fun <K, reified V : Any> CacheClient<K>.setIfAbsent(key: K, value: V, ttl: Duration? = null): Boolean {
    return setIfAbsent(key, value, typeOf<V>(), ttl)
}

suspend inline fun <K, reified V : Any> CacheClient<K>.get(key: K): V? {
    return get(key, typeOf<V>())
}

suspend inline fun <K, reified V : Any> CacheClient<K>.deleteIfEquals(key: K, expected: V) {
    deleteIfEquals(key, expected, typeOf<V>())
}