package library.signing.impl.key

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import library.signing.TokenValidator

internal class TokenValidation(
    issuer: String,
    audience: String,
    algorithm: Algorithm,
) : TokenValidator {

    constructor(issuer: String, audience: String, remoteProviderHostname: String) :
        this(issuer, audience, Algorithm.RSA256(RemoteRsaKeyProvider(remoteProviderHostname)))

    override val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()
}
