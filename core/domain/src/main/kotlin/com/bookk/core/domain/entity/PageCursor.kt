package com.bookk.core.domain.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.io.encoding.Base64
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class PageCursor(
    @ProtoNumber(1) val after: Instant,
    @ProtoNumber(2) val id: Uuid,
) {
    fun encode(): String {
        return encoding.encode(ProtoBuf.encodeToByteArray(this))
    }

    companion object {
        private val encoding = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        fun decodeOrNull(text: String): PageCursor? {
            if (text.isEmpty()) return null
            return runCatching { ProtoBuf.decodeFromByteArray<PageCursor>(encoding.decode(text)) }.getOrNull()
        }
    }
}
