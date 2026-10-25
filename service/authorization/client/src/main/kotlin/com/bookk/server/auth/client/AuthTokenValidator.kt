package com.bookk.server.auth.client

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTCredential
import kotlin.time.Clock
import kotlin.uuid.Uuid

val authTokenValidator: ApplicationCall.(JWTCredential) -> AppPrincipal? = { credentials ->
    val isNotExpired = credentials.payload.expiresAt.time > Clock.System.now().toEpochMilliseconds()
    if (isNotExpired) {
        AppPrincipal(
            authId = Uuid.parse(credentials.payload.getClaim(AuthClaim.AUTH_ID.key).asString()),
            userId = Uuid.parse(credentials.payload.getClaim(AuthClaim.USER_ID.key).asString()),
            deviceId = Uuid.parse(credentials.payload.getClaim(AuthClaim.DEVICE_ID.key).asString())
        )
    } else null
}