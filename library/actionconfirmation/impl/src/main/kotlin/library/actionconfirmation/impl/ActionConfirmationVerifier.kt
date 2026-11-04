package library.actionconfirmation.impl

import com.auth0.jwt.interfaces.DecodedJWT
import library.actionconfirmation.ActionConfirmationClaims
import library.actionconfirmation.ActionConfirmationGrant
import library.signing.TokenValidatorFactory
import library.signing.ValidationType
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.toKotlinInstant

internal class ActionConfirmationProof(val tokenId: String, val remainingLifetime: Duration)

internal class ActionConfirmationVerifier(private val tokenValidatorFactory: TokenValidatorFactory) {

    fun verify(token: String, grant: ActionConfirmationGrant): ActionConfirmationProof? {
        val decoded = runCatching { tokenValidatorFactory.forType(ValidationType.ACTION_CONFIRMATION).verifier.verify(token) }.getOrNull() ?: return null
        val tokenId = decoded.id ?: return null
        val expiresAt = decoded.expiresAtAsInstant?.toKotlinInstant() ?: return null
        val grantsAction = decoded.claim(ActionConfirmationClaims.CLAIM_USER_ID) == grant.userId.toString() &&
            decoded.claim(ActionConfirmationClaims.CLAIM_ACTION) == grant.action &&
            decoded.claim(ActionConfirmationClaims.CLAIM_BUSINESS_ID) == grant.businessId.toString() &&
            decoded.claim(ActionConfirmationClaims.CLAIM_TARGET_ID) == grant.targetId.toString()
        if (!grantsAction) return null
        return ActionConfirmationProof(tokenId, expiresAt - Clock.System.now())
    }

    private fun DecodedJWT.claim(name: String): String? = getClaim(name).asString()
}
