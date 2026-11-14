package library.actionconfirmation.test

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.bookk.core.data.cache.get
import com.bookk.core.data.cache.test.InMemoryCacheClient
import library.actionconfirmation.ActionConfirmationClaims
import library.actionconfirmation.ActionConfirmationGate
import library.actionconfirmation.ActionConfirmationGrant
import library.actionconfirmation.impl.ActionConfirmationGateImpl
import library.actionconfirmation.impl.ActionConfirmationTokenStore
import library.actionconfirmation.impl.ActionConfirmationVerifier
import library.signing.TokenAudience
import library.signing.TokenValidator
import library.signing.TokenValidatorFactory
import library.signing.ValidationType
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource
import kotlin.time.toJavaInstant
import kotlin.uuid.Uuid

class ActionConfirmationTestTokens(val timeSource: TestTimeSource = TestTimeSource()) {
    private val algorithm: Algorithm = Algorithm.HMAC256("action-confirmation-test-secret")
    private val cacheClient = InMemoryCacheClient(timeSource = timeSource)

    private val validatorFactory = object : TokenValidatorFactory {
        override fun forType(type: ValidationType): TokenValidator = object : TokenValidator {
            override val verifier: JWTVerifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .withAudience(
                    if (type == ValidationType.ACTION_CONFIRMATION) {
                        TokenAudience.actionConfirmation
                    } else {
                        TokenAudience.access
                    },
                )
                .build()
        }
    }

    val gate: ActionConfirmationGate =
        ActionConfirmationGateImpl(
            ActionConfirmationVerifier(validatorFactory),
            ActionConfirmationTokenStore(cacheClient),
        )

    fun tokenFor(
        grant: ActionConfirmationGrant,
        tokenId: String? = Uuid.random().toString(),
        expiresIn: Duration = 2.minutes,
    ): String = sign {
        withAudience(TokenAudience.actionConfirmation)
            .withExpiresAt((Clock.System.now() + expiresIn).toJavaInstant())
            .apply { if (tokenId != null) withJWTId(tokenId) }
            .withClaim(ActionConfirmationClaims.CLAIM_USER_ID, grant.userId.toString())
            .withClaim(ActionConfirmationClaims.CLAIM_ACTION, grant.action)
            .withClaim(ActionConfirmationClaims.CLAIM_BUSINESS_ID, grant.businessId.toString())
            .withClaim(ActionConfirmationClaims.CLAIM_TARGET_ID, grant.targetId.toString())
    }

    fun sign(claims: JWTCreator.Builder.() -> JWTCreator.Builder): String = JWT.create()
        .withIssuer(ISSUER)
        .claims()
        .sign(algorithm)

    suspend fun isSpent(tokenId: String): Boolean {
        return cacheClient.get<String, String>(ActionConfirmationTokenStore.key(tokenId)) != null
    }

    private companion object {
        const val ISSUER = "action-confirmation-test-issuer"
    }
}
