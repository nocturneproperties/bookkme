package com.bookk.auth.domain.api.authentication.entity

interface FinishAssertionRequest {
    val requestId: String
    val publicKeyCredentialJson: String
}