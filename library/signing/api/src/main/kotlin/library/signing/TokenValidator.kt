package library.signing

import com.auth0.jwt.JWTVerifier

interface TokenValidator {
    val verifier: JWTVerifier
}