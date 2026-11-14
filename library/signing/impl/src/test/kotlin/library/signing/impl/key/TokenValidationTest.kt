package library.signing.impl.key

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.signing.TokenAudience
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaInstant

internal class TokenValidationTest {

    private class SutFixture {
        val algorithm: Algorithm = Algorithm.HMAC256("test-secret")
        val access = TokenValidation(ISSUER, TokenAudience.access, algorithm)
        val actionConfirmation = TokenValidation(ISSUER, TokenAudience.actionConfirmation, algorithm)
        val serviceQuote = TokenValidation(ISSUER, TokenAudience.serviceQuote, algorithm)

        fun token(audience: String, expiresIn: Duration = 2.minutes): String = JWT.create()
            .withIssuer(ISSUER)
            .withAudience(audience)
            .withExpiresAt((Clock.System.now() + expiresIn).toJavaInstant())
            .sign(algorithm)
    }

    private fun TokenValidation.accepts(token: String) = runCatching { verifier.verify(token) }.isSuccess

    @Test
    fun `should accept an action confirmation token as an action confirmation proof`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val accepted = fixture.actionConfirmation.accepts(fixture.token(TokenAudience.actionConfirmation))

        then()
        assertTrue(accepted)
    }

    @Test
    fun `should not accept an action confirmation token as an access token`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val accepted = fixture.access.accepts(fixture.token(TokenAudience.actionConfirmation))

        then()
        assertFalse(accepted)
    }

    @Test
    fun `should not accept an access token as an action confirmation proof`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val accepted = fixture.actionConfirmation.accepts(fixture.token(TokenAudience.access))

        then()
        assertFalse(accepted)
    }

    @Test
    fun `should accept a service quote as a service quote`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val accepted = fixture.serviceQuote.accepts(fixture.token(TokenAudience.serviceQuote))

        then()
        assertTrue(accepted)
    }

    @Test
    fun `should not accept a service quote as an access token`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val accepted = fixture.access.accepts(fixture.token(TokenAudience.serviceQuote))

        then()
        assertFalse(accepted)
    }

    @Test
    fun `should not accept an access token as a service quote`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val accepted = fixture.serviceQuote.accepts(fixture.token(TokenAudience.access))

        then()
        assertFalse(accepted)
    }

    @Test
    fun `should reject a token issued for the retired confirmation audience`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val accepted = fixture.actionConfirmation.accepts(fixture.token("${TokenAudience.access}/step-up"))

        then()
        assertFalse(accepted)
    }

    @Test
    fun `should not accept an expired action confirmation token`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val accepted = fixture.actionConfirmation.accepts(
            fixture.token(TokenAudience.actionConfirmation, expiresIn = (-1).minutes),
        )

        then()
        assertFalse(accepted)
    }

    private companion object {
        const val ISSUER = "auth.service"
    }
}
