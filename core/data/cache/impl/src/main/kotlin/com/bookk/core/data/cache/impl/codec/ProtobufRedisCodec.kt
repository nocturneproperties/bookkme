package com.bookk.core.data.cache.impl.codec

import io.lettuce.core.codec.RedisCodec
import java.nio.ByteBuffer
import java.nio.charset.Charset


class ProtobufRedisCodec : RedisCodec<String, ByteBuffer> {

    private val charset: Charset = Charset.forName("UTF-8")

    override fun decodeKey(bytes: ByteBuffer): String {
        return charset.decode(bytes).toString()
    }

    override fun decodeValue(bytes: ByteBuffer): ByteBuffer {
        return bytes
    }

    override fun encodeValue(value: ByteBuffer): ByteBuffer {
        return value
    }

    override fun encodeKey(key: String): ByteBuffer {
        return charset.encode(key)
    }
}