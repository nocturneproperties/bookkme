package com.bookk.auth.domain.api.authentication.entity

interface FinishRegistrationRequest {
    val requestId: String
    val publicKeyCredentialJson: String
}