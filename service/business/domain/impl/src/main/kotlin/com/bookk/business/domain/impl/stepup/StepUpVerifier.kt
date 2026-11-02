package com.bookk.business.domain.impl.stepup

import com.auth0.jwt.interfaces.DecodedJWT
import com.bookk.server.auth.client.StepUpClaims
import library.signing.TokenValidatorFactory
import library.signing.ValidationType
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.toKotlinInstant
import kotlin.uuid.Uuid

internal class StepUpProof(val tokenId: String, val remainingLifetime: Duration)

internal class StepUpVerifier(private val tokenValidatorFactory: TokenValidatorFactory) {

    fun verify(token: String, userId: Uuid, action: String, businessId: Uuid, targetId: Uuid): StepUpProof? {
        val decoded = runCatching { tokenValidatorFactory.forType(ValidationType.STEP_UP).verifier.verify(token) }.getOrNull() ?: return null
        val tokenId = decoded.id ?: return null
        val expiresAt = decoded.expiresAtAsInstant?.toKotlinInstant() ?: return null
        val grantsAction = decoded.claim(StepUpClaims.CLAIM_USER_ID) == userId.toString() &&
            decoded.claim(StepUpClaims.CLAIM_ACTION) == action &&
            decoded.claim(StepUpClaims.CLAIM_BUSINESS_ID) == businessId.toString() &&
            decoded.claim(StepUpClaims.CLAIM_TARGET_ID) == targetId.toString()
        if (!grantsAction) return null
        return StepUpProof(tokenId, expiresAt - Clock.System.now())
    }

    private fun DecodedJWT.claim(name: String): String? = getClaim(name).asString()
}
