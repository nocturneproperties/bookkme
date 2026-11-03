package com.bookk.user.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant

@Serializable
data class LegalDocumentVersion(
    @ProtoNumber(1) val document: LegalDocument,
    @ProtoNumber(2) val version: String,
    @ProtoNumber(3) val url: String,
    @ProtoNumber(4) val effectiveAt: Instant,
) {
    fun isInEffect(now: Instant): Boolean = effectiveAt <= now

    companion object {
        fun stub(
            document: LegalDocument = LegalDocument.PRIVACY_POLICY,
            version: String = "2026-11-03",
            url: String = "https://bookk.me/privacy",
            effectiveAt: Instant = Instant.fromEpochMilliseconds(0),
        ) = LegalDocumentVersion(document, version, url, effectiveAt)
    }
}
