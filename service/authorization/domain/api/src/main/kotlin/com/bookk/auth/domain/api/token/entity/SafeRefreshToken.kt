package com.bookk.auth.domain.api.token.entity

import java.security.MessageDigest
import java.util.Base64
import kotlin.uuid.Uuid

class SafeRefreshToken(
    val id: Uuid,
    val secretHash: String
) {
    companion object {
        fun from(id: Uuid?, hash: String?): SafeRefreshToken? {
            if (id == null || hash == null) return null
            return SafeRefreshToken(id, hash)
        }
    }
}

class UnsafeRefreshToken(
    val id: Uuid,
    val secret: ByteArray
) {
    val secretHash: String = MessageDigest
        .getInstance("SHA-256")
        .digest(secret)
        .toHexString()

    val token: String
        get() = buildString {
            append(encoder.encodeToString(id.toByteArray()))
            append(".")
            append(encoder.encodeToString(secret))
        }

    companion object {

        private val encoder = Base64.getUrlEncoder().withoutPadding()

        fun from(id: Uuid?, secret: ByteArray?): UnsafeRefreshToken? {
            if (id == null || secret == null) return null
            return UnsafeRefreshToken(id, secret)
        }
    }
}