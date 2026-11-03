package library.stepup.test

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.bookk.core.data.cache.get
import com.bookk.core.data.cache.test.InMemoryCacheClient
import library.signing.TokenAudience
import library.signing.TokenValidator
import library.signing.TokenValidatorFactory
import library.signing.ValidationType
import library.stepup.StepUpClaims
import library.stepup.StepUpGate
import library.stepup.StepUpGrant
import library.stepup.impl.StepUpGateImpl
import library.stepup.impl.StepUpTokenStore
import library.stepup.impl.StepUpVerifier
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource
import kotlin.time.toJavaInstant
import kotlin.uuid.Uuid

class StepUpTestTokens(val timeSource: TestTimeSource = TestTimeSource()) {
    private val algorithm: Algorithm = Algorithm.HMAC256("step-up-test-secret")
    private val cacheClient = InMemoryCacheClient(timeSource = timeSource)

    private val validatorFactory = object : TokenValidatorFactory {
        override fun forType(type: ValidationType): TokenValidator = object : TokenValidator {
            override val verifier: JWTVerifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .withAudience(if (type == ValidationType.STEP_UP) TokenAudience.stepUp else TokenAudience.access)
                .build()
        }
    }

    val gate: StepUpGate = StepUpGateImpl(StepUpVerifier(validatorFactory), StepUpTokenStore(cacheClient))

    fun tokenFor(grant: StepUpGrant, tokenId: String? = Uuid.random().toString(), expiresIn: Duration = 2.minutes): String = sign {
        withAudience(TokenAudience.stepUp)
            .withExpiresAt((Clock.System.now() + expiresIn).toJavaInstant())
            .apply { if (tokenId != null) withJWTId(tokenId) }
            .withClaim(StepUpClaims.CLAIM_USER_ID, grant.userId.toString())
            .withClaim(StepUpClaims.CLAIM_ACTION, grant.action)
            .withClaim(StepUpClaims.CLAIM_BUSINESS_ID, grant.businessId.toString())
            .withClaim(StepUpClaims.CLAIM_TARGET_ID, grant.targetId.toString())
    }

    fun sign(claims: JWTCreator.Builder.() -> JWTCreator.Builder): String = JWT.create()
        .withIssuer(ISSUER)
        .claims()
        .sign(algorithm)

    suspend fun isSpent(tokenId: String): Boolean = cacheClient.get<String, String>(StepUpTokenStore.key(tokenId)) != null

    private companion object {
        const val ISSUER = "step-up-test-issuer"
    }
}
