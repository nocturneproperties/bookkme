package library.stepup.impl

import com.auth0.jwt.interfaces.DecodedJWT
import library.signing.TokenValidatorFactory
import library.signing.ValidationType
import library.stepup.StepUpClaims
import library.stepup.StepUpGrant
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.toKotlinInstant

internal class StepUpProof(val tokenId: String, val remainingLifetime: Duration)

internal class StepUpVerifier(private val tokenValidatorFactory: TokenValidatorFactory) {

    fun verify(token: String, grant: StepUpGrant): StepUpProof? {
        val decoded = runCatching { tokenValidatorFactory.forType(ValidationType.STEP_UP).verifier.verify(token) }.getOrNull() ?: return null
        val tokenId = decoded.id ?: return null
        val expiresAt = decoded.expiresAtAsInstant?.toKotlinInstant() ?: return null
        val grantsAction = decoded.claim(StepUpClaims.CLAIM_USER_ID) == grant.userId.toString() &&
            decoded.claim(StepUpClaims.CLAIM_ACTION) == grant.action &&
            decoded.claim(StepUpClaims.CLAIM_BUSINESS_ID) == grant.businessId.toString() &&
            decoded.claim(StepUpClaims.CLAIM_TARGET_ID) == grant.targetId.toString()
        if (!grantsAction) return null
        return StepUpProof(tokenId, expiresAt - Clock.System.now())
    }

    private fun DecodedJWT.claim(name: String): String? = getClaim(name).asString()
}
