package com.bookk.server.auth.client

import com.auth0.jwt.interfaces.Payload
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTCredential
import kotlin.time.Clock
import kotlin.uuid.Uuid

val authTokenValidator: ApplicationCall.(JWTCredential) -> AppPrincipal? = { credentials ->
    val isNotExpired = credentials.payload.expiresAt.time > Clock.System.now().toEpochMilliseconds()
    if (isNotExpired) credentials.payload.toPrincipal() else null
}

private fun Payload.toPrincipal(): AppPrincipal? {
    val authId = uuidClaim(AuthClaim.AUTH_ID) ?: return null
    val userId = uuidClaim(AuthClaim.USER_ID) ?: return null
    val deviceId = uuidClaim(AuthClaim.DEVICE_ID) ?: return null
    return AppPrincipal(authId = authId, userId = userId, deviceId = deviceId)
}

private fun Payload.uuidClaim(claim: AuthClaim): Uuid? = getClaim(claim.key).asString()?.let(Uuid::parseOrNull)
