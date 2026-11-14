package library.actionconfirmation.impl

import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.yield
import library.actionconfirmation.ActionConfirmationGrant
import library.actionconfirmation.test.ActionConfirmationTestTokens
import library.signing.TokenAudience
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaInstant
import kotlin.uuid.Uuid

internal class ActionConfirmationGateImplTest {

    private class Rejected : RuntimeException()

    private class SutFixture {
        val tokens = ActionConfirmationTestTokens()
        val grant = ActionConfirmationGrant.clientDeletion(
            userId = Uuid.random(),
            businessId = Uuid.random(),
            clientId = Uuid.random(),
        )
        var actionRuns = 0

        suspend fun spend(
            token: String,
            grant: ActionConfirmationGrant = this.grant,
            action: suspend () -> String = {
                "done"
            },
        ): Result<String> = runSuspendCatching {
            tokens.gate.spend(token, grant, ::Rejected) {
                actionRuns++
                action()
            }
        }
    }

    @Test
    fun `should run the action for a confirmation token naming caller, action, business and target`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.spend(fixture.tokens.tokenFor(fixture.grant))

        then()
        assertEquals("done", result.getOrNull())
        assertEquals(1, fixture.actionRuns)
    }

    @Test
    fun `should keep the token spent after the action succeeded`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = fixture.tokens.tokenFor(fixture.grant, tokenId = "token-id")

        whenn()
        fixture.spend(token)

        then()
        assertTrue(fixture.tokens.isSpent("token-id"))
    }

    @Test
    fun `should reject an action confirmation token that was already used`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = fixture.tokens.tokenFor(fixture.grant)
        fixture.spend(token)

        whenn()
        val result = fixture.spend(token)

        then()
        assertTrue(result.exceptionOrNull() is Rejected)
        assertEquals(1, fixture.actionRuns)
    }

    @Test
    fun `should let only one of two concurrent requests spend the same token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = fixture.tokens.tokenFor(fixture.grant)
        val firstInside = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        whenn()
        val first = async {
            fixture.spend(token) {
                firstInside.complete(Unit)
                releaseFirst.await()
                "first"
            }
        }
        firstInside.await()
        val second = fixture.spend(token)
        releaseFirst.complete(Unit)

        then()
        assertEquals("first", first.await().getOrNull())
        assertTrue(second.exceptionOrNull() is Rejected)
    }

    @Test
    fun `should give the token back when the action fails, with the action's own failure`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = fixture.tokens.tokenFor(fixture.grant, tokenId = "token-id")
        val failure = IllegalStateException("deletion failed")

        whenn()
        val result = fixture.spend(token) { throw failure }

        then()
        assertEquals(failure, result.exceptionOrNull())
        assertFalse(fixture.tokens.isSpent("token-id"))
        assertTrue(fixture.spend(token).isSuccess)
    }

    @Test
    fun `should give the token back when the request is cancelled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = fixture.tokens.tokenFor(fixture.grant, tokenId = "token-id")
        val inside = CompletableDeferred<Unit>()

        whenn()
        val request = async {
            fixture.tokens.gate.spend(token, fixture.grant, ::Rejected) {
                inside.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        inside.await()
        request.cancel()
        val outcome = runCatching { request.await() }
        yield()

        then()
        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertFalse(fixture.tokens.isSpent("token-id"))
    }

    @Test
    fun `should forget a spent token once it has expired`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.spend(fixture.tokens.tokenFor(fixture.grant, tokenId = "token-id"))
        fixture.tokens.timeSource += 3.minutes

        whenn()
        val spent = fixture.tokens.isSpent("token-id")

        then()
        assertFalse(spent)
    }

    @Test
    fun `should reject an action confirmation token of another user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.spend(fixture.tokens.tokenFor(fixture.grant.copy(userId = Uuid.random())))

        then()
        assertTrue(result.exceptionOrNull() is Rejected)
        assertEquals(0, fixture.actionRuns)
    }

    @Test
    fun `should reject an action confirmation token for another action`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.spend(fixture.tokens.tokenFor(fixture.grant.copy(action = "business.delete")))

        then()
        assertTrue(result.exceptionOrNull() is Rejected)
        assertEquals(0, fixture.actionRuns)
    }

    @Test
    fun `should reject an action confirmation token for another business`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.spend(fixture.tokens.tokenFor(fixture.grant.copy(businessId = Uuid.random())))

        then()
        assertTrue(result.exceptionOrNull() is Rejected)
        assertEquals(0, fixture.actionRuns)
    }

    @Test
    fun `should reject an action confirmation token for another target`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.spend(fixture.tokens.tokenFor(fixture.grant.copy(targetId = Uuid.random())))

        then()
        assertTrue(result.exceptionOrNull() is Rejected)
        assertEquals(0, fixture.actionRuns)
    }

    @Test
    fun `should reject an action confirmation token without an id`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.spend(fixture.tokens.tokenFor(fixture.grant, tokenId = null))

        then()
        assertTrue(result.exceptionOrNull() is Rejected)
    }

    @Test
    fun `should reject an expired action confirmation token`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.spend(fixture.tokens.tokenFor(fixture.grant, expiresIn = (-1).seconds))

        then()
        assertTrue(result.exceptionOrNull() is Rejected)
    }

    @Test
    fun `should reject an action confirmation token without an expiry`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val neverExpiring = fixture.tokens.sign {
            withAudience(TokenAudience.actionConfirmation)
                .withJWTId("token-id")
                .withClaim("user_id", fixture.grant.userId.toString())
                .withClaim("action", fixture.grant.action)
                .withClaim("business_id", fixture.grant.businessId.toString())
                .withClaim("target_id", fixture.grant.targetId.toString())
        }

        whenn()
        val result = fixture.spend(neverExpiring)

        then()
        assertTrue(result.exceptionOrNull() is Rejected)
    }

    @Test
    fun `should reject an access token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val accessToken = fixture.tokens.sign {
            withAudience(TokenAudience.access)
                .withJWTId("token-id")
                .withExpiresAt((Clock.System.now() + 2.minutes).toJavaInstant())
                .withClaim("user_id", fixture.grant.userId.toString())
                .withClaim("action", fixture.grant.action)
                .withClaim("business_id", fixture.grant.businessId.toString())
                .withClaim("target_id", fixture.grant.targetId.toString())
        }

        whenn()
        val result = fixture.spend(accessToken)

        then()
        assertTrue(result.exceptionOrNull() is Rejected)
    }

    @Test
    fun `should reject a malformed token`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.spend("not-a-token")

        then()
        assertTrue(result.exceptionOrNull() is Rejected)
    }
}
