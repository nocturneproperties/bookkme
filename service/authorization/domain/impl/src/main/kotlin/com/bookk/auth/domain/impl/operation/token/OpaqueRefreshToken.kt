package com.bookk.auth.domain.impl.operation.token

import com.bookk.auth.domain.api.token.entity.SafeRefreshToken
import com.bookk.auth.domain.api.token.entity.UnsafeRefreshToken
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.uuid.Uuid

internal object OpaqueRefreshToken {
    private const val SECRET_BYTES = 32

    private val secureRandom = SecureRandom()

    fun generate(): UnsafeRefreshToken {
        return UnsafeRefreshToken(randomTokenId(), randomSecret())
    }

    fun parse(token: String): UnsafeRefreshToken? {
        return runCatching {
            token.split('.', limit = 2)
                .map { Base64.getUrlDecoder().decode(it) }
                .takeIf { it.size == 2 }
                ?.let { (id, secret) ->
                    UnsafeRefreshToken(Uuid.fromByteArray(id), secret)
                }
        }.getOrNull()
    }

    fun matches(unsafe: UnsafeRefreshToken, safe: SafeRefreshToken): Boolean {
        if (unsafe.id != safe.id) return false
        return MessageDigest.isEqual(
            unsafe.secretHash.toByteArray(),
            safe.secretHash.toByteArray()
        )
    }

    private fun randomTokenId(): Uuid {
        return Uuid.random()
    }

    private fun randomSecret(): ByteArray {
        return ByteArray(SECRET_BYTES)
            .also { secureRandom.nextBytes(it) }
    }
}
