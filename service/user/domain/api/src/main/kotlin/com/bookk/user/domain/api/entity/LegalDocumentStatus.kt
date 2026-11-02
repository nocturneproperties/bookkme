package com.bookk.user.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant

@Serializable
data class LegalDocumentStatus(
    @ProtoNumber(1) val current: LegalDocumentVersion,
    @ProtoNumber(2) val acceptedAt: Instant?,
) {
    companion object {
        fun stub(
            current: LegalDocumentVersion = LegalDocumentVersion.stub(),
            acceptedAt: Instant? = null,
        ) = LegalDocumentStatus(current, acceptedAt)
    }
}
