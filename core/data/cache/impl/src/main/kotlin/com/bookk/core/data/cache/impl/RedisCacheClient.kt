package com.bookk.core.data.cache.impl

import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.impl.codec.ProtobufRedisCodec
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.coroutines
import io.lettuce.core.support.ConnectionPoolSupport
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import java.nio.ByteBuffer
import kotlin.reflect.KType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisCacheClient(
    host: String,
    port: Int,
    password: CharSequence,
    private val protobuf: ProtoBuf
) : CacheClient<String> {

    private val client = RedisClient.create(
        RedisURI.builder()
            .withHost(host)
            .withPort(port)
            .withPassword(password)
            .withTimeout(5.seconds.toJavaDuration())
            .build()
    )
    private val connectionPool = ConnectionPoolSupport.createSoftReferenceObjectPool {
        client.connect(ProtobufRedisCodec())
    }

    override suspend fun <V : Any> set(key: String, value: V, kType: KType, expiration: Duration?) {
        withPooledConnection {
            val serializer = protobuf.serializersModule.serializer(kType)
            coroutines().apply {
                set(
                    key,
                    ByteBuffer.wrap(protobuf.encodeToByteArray(serializer, value)),
                    expiration?.let { SetArgs.Builder.ex(it.toJavaDuration()) } ?: SetArgs()
                )
            }
        }
    }

    override suspend fun <V : Any> setIfAbsent(key: String, value: V, kType: KType, expiration: Duration?): Boolean {
        return withPooledConnection {
            val serializer = protobuf.serializersModule.serializer(kType)
            val setArgs = SetArgs().nx()
            expiration?.let { setArgs.ex(it.toJavaDuration()) }
            coroutines().set(key, ByteBuffer.wrap(protobuf.encodeToByteArray(serializer, value)), setArgs) == "OK"
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <V : Any> get(key: String, kType: KType): V? {
        return withPooledConnection {
            val deserializer = protobuf.serializersModule.serializer(kType)
            coroutines().get(key)?.let { buffer ->
                val array = ByteArray(buffer.remaining()).also { buffer.get(it) }
                protobuf.decodeFromByteArray(deserializer, array) as V
            }
        }
    }

    override suspend fun withTransaction(action: suspend CacheClient<String>.() -> Unit) {
        withPooledConnection {
            val commands = async()
            commands.multi().await()
            try {
                action(commands.asTransactionCache())
            } catch (failure: Throwable) {
                withContext(NonCancellable) { commands.discard().await() }
                throw failure
            }
            commands.exec().await().filterIsInstance<Throwable>().firstOrNull()?.let { throw it }
        }
    }

    override suspend fun delete(key: String) {
        withPooledConnection {
            coroutines().del(key)
        }
    }

    override suspend fun <V : Any> deleteIfEquals(key: String, expected: V, kType: KType) {
        withPooledConnection {
            val serializer = protobuf.serializersModule.serializer(kType)
            coroutines().eval<Long>(
                DELETE_IF_EQUALS_SCRIPT,
                ScriptOutputType.INTEGER,
                arrayOf(key),
                ByteBuffer.wrap(protobuf.encodeToByteArray(serializer, expected))
            )
        }
    }

    private inline fun <T> withPooledConnection(action: StatefulRedisConnection<String, ByteBuffer>.() -> T): T =
        connectionPool.borrowObject().use { connection -> connection.action() }

    override fun close() {
        connectionPool.close()
        client.shutdown()
    }

    private fun RedisAsyncCommands<String, ByteBuffer>.asTransactionCache() =
        object : CacheClient<String> {
            override suspend fun <V : Any> set(key: String, value: V, kType: KType, expiration: Duration?) {
                val serializer = protobuf.serializersModule.serializer(kType)
                this@asTransactionCache.set(
                    key,
                    ByteBuffer.wrap(protobuf.encodeToByteArray(serializer, value)),
                    expiration?.let { SetArgs.Builder.ex(it.toJavaDuration()) } ?: SetArgs()
                )
            }

            override suspend fun <V : Any> setIfAbsent(key: String, value: V, kType: KType, expiration: Duration?): Boolean {
                throw UnsupportedOperationException("Conditional set inside transaction is not supported, its result is only known after EXEC")
            }

            override suspend fun <V : Any> get(key: String, kType: KType): V? {
                throw UnsupportedOperationException("Reading inside transaction is not supported, values are only known after EXEC")
            }

            override suspend fun delete(key: String) {
                this@asTransactionCache.del(key)
            }

            override suspend fun <V : Any> deleteIfEquals(key: String, expected: V, kType: KType) {
                throw UnsupportedOperationException("Conditional delete inside transaction is not supported, the compared value is only known after EXEC")
            }

            override suspend fun withTransaction(action: suspend CacheClient<String>.() -> Unit) {
                throw UnsupportedOperationException("Transaction inside transaction is not supported")
            }

            override fun close() {
                throw UnsupportedOperationException("Redis transaction is not closeable")
            }
        }

    private companion object {
        const val DELETE_IF_EQUALS_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end"
    }
}
