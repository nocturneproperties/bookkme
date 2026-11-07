package com.bookk.user.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class AcceptedLegalDocuments(
    @ProtoNumber(1) val privacyPolicy: String?,
    @ProtoNumber(2) val termsOfService: String?,
) {
    fun versions(): Map<LegalDocument, String> = buildMap {
        privacyPolicy?.let { put(LegalDocument.PRIVACY_POLICY, it) }
        termsOfService?.let { put(LegalDocument.TERMS_OF_SERVICE, it) }
    }
}
