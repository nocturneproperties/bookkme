package com.bookk.server.auth.client

enum class AuthClaim(val key: String) {
    AUTH_ID("auth_id"),
    USER_ID("user_id"),
    DEVICE_ID("device_id")
}