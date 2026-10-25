package library.signing.impl.key

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import com.bookk.core.AppLevelConstants
import library.signing.GetActiveSigningKey
import library.signing.TokenIssuer
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.toJavaInstant

internal class TokenIssuerImpl(
    private val getActiveSigningKey: GetActiveSigningKey
): TokenIssuer {
    override suspend fun issue(ttl: Duration, modifier: JWTCreator.Builder.() -> JWTCreator.Builder): String {
        val signingKey = getActiveSigningKey().getOrThrow()
        val now = Clock.System.now()

        return JWT.create()
            .withIssuer(AppLevelConstants.serviceHostName)
            .withKeyId(signingKey.id.toString())
            .withIssuedAt(now.toJavaInstant())
            .withExpiresAt(now.plus(ttl).toJavaInstant())
            .withNotBefore(now.toJavaInstant())
            .modifier()
            .sign(Algorithm.RSA256(rsaProviderFrom(signingKey)))

    }
}