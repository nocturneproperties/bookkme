package library.signing.impl.key

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import library.signing.TokenValidator

internal class TokenValidation(
    issuer: String,
    audience: String,
    remoteProviderHostname: String
) : TokenValidator {

    override val verifier: JWTVerifier = JWT
        .require(Algorithm.RSA256(RemoteRsaKeyProvider(remoteProviderHostname)))
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

}