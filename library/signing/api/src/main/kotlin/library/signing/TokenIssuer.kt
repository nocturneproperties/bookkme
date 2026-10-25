package library.signing

import com.auth0.jwt.JWTCreator
import kotlin.time.Duration

interface TokenIssuer {
    suspend fun issue(ttl: Duration, modifier: JWTCreator.Builder.() -> JWTCreator.Builder): String
}