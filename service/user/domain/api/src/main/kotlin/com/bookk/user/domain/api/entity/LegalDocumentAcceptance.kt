package com.bookk.user.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant

@Serializable
data class LegalDocumentAcceptance(
    @ProtoNumber(1) val document: LegalDocument,
    @ProtoNumber(2) val version: String,
    @ProtoNumber(3) val acceptedAt: Instant,
) {
    companion object {
        fun stub(
            document: LegalDocument = LegalDocument.PRIVACY_POLICY,
            version: String = "2026-11-03",
            acceptedAt: Instant = Instant.fromEpochMilliseconds(0),
        ) = LegalDocumentAcceptance(document, version, acceptedAt)
    }
}
