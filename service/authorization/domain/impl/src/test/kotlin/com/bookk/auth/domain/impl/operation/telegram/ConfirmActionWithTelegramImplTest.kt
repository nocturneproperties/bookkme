package com.bookk.auth.domain.impl.operation.telegram

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.bookk.auth.domain.api.actionconfirmation.entity.ConfirmableAction
import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.telegram.entity.TelegramActionConfirmationRequest
import com.bookk.auth.domain.api.telegram.entity.TelegramConfirmationChallenge
import com.bookk.auth.domain.api.telegram.operation.ConfirmActionWithTelegram
import com.bookk.auth.domain.api.telegram.operation.SignInWithTelegram
import com.bookk.auth.domain.api.telegram.operation.TelegramConfirmationUnavailable
import com.bookk.auth.domain.datasource.TelegramConfirmationDataSource
import com.bookk.auth.domain.datasource.TelegramIdentityDataSource
import com.bookk.auth.domain.impl.operation.actionconfirmation.ACTION_CONFIRMATION_TTL
import com.bookk.auth.domain.impl.telegram.Ed25519TelegramInitDataVerifier
import com.bookk.auth.domain.impl.telegram.TelegramInitDataSigner
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.mockedTransactionManager
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AuthEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.actionconfirmation.ActionConfirmationClaims
import library.actionconfirmation.ActionConfirmationGrant
import library.actionconfirmation.test.ActionConfirmationTestTokens
import library.signing.TokenIssuer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.uuid.Uuid

internal class ConfirmActionWithTelegramImplTest {

    private class SutFixture(botId: Long? = BOT_ID) {
        val now: Instant = Instant.parse("2026-11-26T10:00:00Z")
        val userId: Uuid = Uuid.random()
        val signer = TelegramInitDataSigner(BOT_ID)
        val telegramIdentityDataSource = mockk<TelegramIdentityDataSource>()
        val confirmationDataSource = mockk<TelegramConfirmationDataSource>()
        val tokens = ActionConfirmationTestTokens()
        val tokenIssuer = mockk<TokenIssuer>()
        val transactionManager = mockedTransactionManager()
        val eventProducer = mockk<StandardEventProducer>()
        val publishedInTransaction = mutableListOf<Boolean>()
        private val clock = object : Clock {
            override fun now(): Instant {
                return this@SutFixture.now
            }
        }
        val sut = ConfirmActionWithTelegramImpl(
            Ed25519TelegramInitDataVerifier(botId = botId, clock = clock, telegramPublicKey = signer.publicKey),
            confirmationDataSource,
            telegramIdentityDataSource,
            tokenIssuer,
            eventProducer,
            transactionManager,
        )

        init {
            coEvery { eventProducer.send(any<AuthEvent.TelegramActionConfirmed>(), any()) } coAnswers {
                publishedInTransaction += transactionManager.isInTransaction()
            }
            coEvery { tokenIssuer.issue(any(), any()) } coAnswers {
                val claims = secondArg<JWTCreator.Builder.() -> JWTCreator.Builder>()
                val expiresAt = Clock.System.now() + ACTION_CONFIRMATION_TTL
                tokens.sign { withExpiresAt(expiresAt.toJavaInstant()).claims() }
            }
        }

        fun launch(
            startParam: String?,
            authDate: Instant = now - 1.minutes,
        ): TelegramActionConfirmationRequest {
            val extra = startParam?.let { mapOf("start_param" to it) }.orEmpty()
            return TelegramActionConfirmationRequest(signer.signed(authDate = authDate, extra = extra))
        }

        fun challengeStored(challenge: TelegramConfirmationChallenge) {
            coEvery { confirmationDataSource.getChallenge(CHALLENGE) } returns challenge
            coEvery { confirmationDataSource.takeChallenge(CHALLENGE) } returnsMany listOf(challenge, null)
        }

        fun ownChallenge(action: ConfirmableAction = ConfirmableAction.DELETE_ACCOUNT): TelegramConfirmationChallenge {
            return TelegramConfirmationChallenge(userId, action, Uuid.NIL, userId)
        }

        fun telegramAccountOf(ownerUserId: Uuid?) {
            coEvery { telegramIdentityDataSource.getAccountByTelegramUser(TELEGRAM_USER_ID) } returns
                ownerUserId?.let { Authentication.stub(userId = it) }
        }

        suspend fun spend(token: String, grant: ActionConfirmationGrant): Result<String> = runSuspendCatching {
            tokens.gate.spend(token, grant, rejection = { IllegalStateException("rejected") }) { "done" }
        }
    }

    @Test
    fun `should issue a token that the real gate accepts for the caller's account deletion`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.challengeStored(fixture.ownChallenge(ConfirmableAction.DELETE_ACCOUNT))
        fixture.telegramAccountOf(fixture.userId)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.launch("op_delete-account_$CHALLENGE"))

        then()
        val token = result.getOrThrow().token
        assertEquals("done", fixture.spend(token, ActionConfirmationGrant.accountDeletion(fixture.userId)).getOrThrow())
        coVerify(exactly = 1) { fixture.confirmationDataSource.takeChallenge(CHALLENGE) }
    }

    @Test
    fun `should issue a token that the real gate accepts for the caller's personal data export`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.challengeStored(fixture.ownChallenge(ConfirmableAction.EXPORT_PERSONAL_DATA))
        fixture.telegramAccountOf(fixture.userId)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.launch("op_export-personal_$CHALLENGE"))

        then()
        val token = result.getOrThrow().token
        val grant = ActionConfirmationGrant.personalDataExport(fixture.userId)
        assertEquals("done", fixture.spend(token, grant).getOrThrow())
    }

    @Test
    fun `should take the action, business and target only from the stored challenge`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val targetId = Uuid.random()
        fixture.challengeStored(
            TelegramConfirmationChallenge(fixture.userId, ConfirmableAction.EXPORT_PERSONAL_DATA, businessId, targetId),
        )
        fixture.telegramAccountOf(fixture.userId)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.launch("op_export-personal_$CHALLENGE"))

        then()
        val decoded = JWT.decode(result.getOrThrow().token)
        assertEquals(fixture.userId.toString(), decoded.getClaim(ActionConfirmationClaims.CLAIM_USER_ID).asString())
        assertEquals(
            ActionConfirmationClaims.ACTION_EXPORT_PERSONAL_DATA,
            decoded.getClaim(ActionConfirmationClaims.CLAIM_ACTION).asString(),
        )
        assertEquals(businessId.toString(), decoded.getClaim(ActionConfirmationClaims.CLAIM_BUSINESS_ID).asString())
        assertEquals(targetId.toString(), decoded.getClaim(ActionConfirmationClaims.CLAIM_TARGET_ID).asString())
    }

    @Test
    fun `should not let an export confirmation pass as an account deletion`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.challengeStored(fixture.ownChallenge(ConfirmableAction.EXPORT_PERSONAL_DATA))
        fixture.telegramAccountOf(fixture.userId)

        whenn()
        val token = fixture.sut(fixture.userId, fixture.launch("op_export-personal_$CHALLENGE")).getOrThrow().token

        then()
        assertTrue(fixture.spend(token, ActionConfirmationGrant.accountDeletion(fixture.userId)).isFailure)
    }

    @Test
    fun `should refuse init data not signed by Telegram without taking the challenge`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val forged = fixture.launch("op_delete-account_$CHALLENGE").initData
            .replace(Regex("signature=[^&]+"), "signature=${"A".repeat(86)}")

        whenn()
        val result = fixture.sut(fixture.userId, TelegramActionConfirmationRequest(forged))

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataInvalid) { result.toString() }
        coVerify(exactly = 0) { fixture.confirmationDataSource.getChallenge(any()) }
        coVerify(exactly = 0) { fixture.confirmationDataSource.takeChallenge(any()) }
    }

    @Test
    fun `should refuse init data older than 5 minutes as expired without taking the challenge`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val stale = fixture.launch("op_delete-account_$CHALLENGE", authDate = fixture.now - 5.minutes - 1.seconds)

        whenn()
        val result = fixture.sut(fixture.userId, stale)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataExpired) { result.toString() }
        coVerify(exactly = 0) { fixture.confirmationDataSource.getChallenge(any()) }
        coVerify(exactly = 0) { fixture.confirmationDataSource.takeChallenge(any()) }
    }

    @Test
    fun `should refuse init data dated in the future without taking the challenge`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val future = fixture.launch("op_delete-account_$CHALLENGE", authDate = fixture.now + 2.minutes)

        whenn()
        val result = fixture.sut(fixture.userId, future)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataInvalid) { result.toString() }
        coVerify(exactly = 0) { fixture.confirmationDataSource.getChallenge(any()) }
        coVerify(exactly = 0) { fixture.confirmationDataSource.takeChallenge(any()) }
    }

    @Test
    fun `should answer unavailable without reading the init data when no bot id is set`() = runUnitTest {
        given()
        val fixture = SutFixture(botId = null)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.launch("op_delete-account_$CHALLENGE"))

        then()
        assertTrue(result.exceptionOrNull() is TelegramConfirmationUnavailable) { result.toString() }
    }

    @Test
    fun `should refuse a launch that is not a confirmation without taking any challenge`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val startParams = listOf(
            null,
            "anna-barber",
            Uuid.random().toString(),
            "op_delete-client_$CHALLENGE",
            "op_newer-kind_$CHALLENGE",
            "op_delete-account_not-a-challenge",
        )

        whenn()
        val results = startParams.map { fixture.sut(fixture.userId, fixture.launch(it)) }

        then()
        assertTrue(
            results.all {
                it.exceptionOrNull() is ConfirmActionWithTelegram.Error.TelegramConfirmationExpired
            },
        ) {
            results.toString()
        }
        coVerify(exactly = 0) { fixture.confirmationDataSource.getChallenge(any()) }
        coVerify(exactly = 0) { fixture.confirmationDataSource.takeChallenge(any()) }
    }

    @Test
    fun `should refuse a launch whose kind differs from the challenge's action without spending it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.challengeStored(fixture.ownChallenge(ConfirmableAction.EXPORT_PERSONAL_DATA))
        fixture.telegramAccountOf(fixture.userId)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.launch("op_delete-account_$CHALLENGE"))

        then()
        assertTrue(result.exceptionOrNull() is ConfirmActionWithTelegram.Error.TelegramConfirmationExpired) {
            result.toString()
        }
        coVerify(exactly = 0) { fixture.tokenIssuer.issue(any(), any()) }
        coVerify(exactly = 0) { fixture.confirmationDataSource.takeChallenge(any()) }
    }

    @Test
    fun `should refuse an unknown or expired challenge`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.confirmationDataSource.getChallenge(CHALLENGE) } returns null

        whenn()
        val result = fixture.sut(fixture.userId, fixture.launch("op_delete-account_$CHALLENGE"))

        then()
        assertTrue(result.exceptionOrNull() is ConfirmActionWithTelegram.Error.TelegramConfirmationExpired) {
            result.toString()
        }
    }

    @Test
    fun `should refuse a challenge that was already used`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.challengeStored(fixture.ownChallenge())
        fixture.telegramAccountOf(fixture.userId)
        val request = fixture.launch("op_delete-account_$CHALLENGE")
        fixture.sut(fixture.userId, request).getOrThrow()

        whenn()
        val second = fixture.sut(fixture.userId, request)

        then()
        assertTrue(second.exceptionOrNull() is ConfirmActionWithTelegram.Error.TelegramConfirmationExpired) {
            second.toString()
        }
        coVerify(exactly = 1) { fixture.tokenIssuer.issue(any(), any()) }
    }

    @Test
    fun `should refuse a challenge another request took after it was checked`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.confirmationDataSource.getChallenge(CHALLENGE) } returns fixture.ownChallenge()
        coEvery { fixture.confirmationDataSource.takeChallenge(CHALLENGE) } returns null
        fixture.telegramAccountOf(fixture.userId)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.launch("op_delete-account_$CHALLENGE"))

        then()
        assertTrue(result.exceptionOrNull() is ConfirmActionWithTelegram.Error.TelegramConfirmationExpired) {
            result.toString()
        }
        coVerify(exactly = 0) { fixture.tokenIssuer.issue(any(), any()) }
    }

    @Test
    fun `should refuse another user's challenge without spending it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val otherUserId = Uuid.random()
        fixture.challengeStored(
            TelegramConfirmationChallenge(otherUserId, ConfirmableAction.DELETE_ACCOUNT, Uuid.NIL, otherUserId),
        )
        fixture.telegramAccountOf(fixture.userId)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.launch("op_delete-account_$CHALLENGE"))

        then()
        assertTrue(result.exceptionOrNull() is ConfirmActionWithTelegram.Error.TelegramConfirmationExpired) {
            result.toString()
        }
        coVerify(exactly = 0) { fixture.tokenIssuer.issue(any(), any()) }
        coVerify(exactly = 0) { fixture.confirmationDataSource.takeChallenge(any()) }
    }

    @Test
    fun `should refuse another user's Telegram account without spending the challenge`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.challengeStored(fixture.ownChallenge())
        fixture.telegramAccountOf(Uuid.random())

        whenn()
        val result = fixture.sut(fixture.userId, fixture.launch("op_delete-account_$CHALLENGE"))

        then()
        assertTrue(result.exceptionOrNull() is ConfirmActionWithTelegram.Error.TelegramAccountMismatch) {
            result.toString()
        }
        coVerify(exactly = 0) { fixture.tokenIssuer.issue(any(), any()) }
        coVerify(exactly = 0) { fixture.confirmationDataSource.takeChallenge(any()) }
    }

    @Test
    fun `should refuse a launch opened from a Telegram account linked to no user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.challengeStored(fixture.ownChallenge())
        fixture.telegramAccountOf(null)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.launch("op_delete-account_$CHALLENGE"))

        then()
        assertTrue(result.exceptionOrNull() is ConfirmActionWithTelegram.Error.TelegramAccountMismatch) {
            result.toString()
        }
        coVerify(exactly = 0) { fixture.tokenIssuer.issue(any(), any()) }
        coVerify(exactly = 0) { fixture.confirmationDataSource.takeChallenge(any()) }
    }

    @Test
    fun `should publish the confirmed challenge so notifications can mark the Telegram message`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.challengeStored(fixture.ownChallenge())
        fixture.telegramAccountOf(fixture.userId)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.launch("op_delete-account_$CHALLENGE"))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.eventProducer.send(
                match<AuthEvent.TelegramActionConfirmed> { it.userId == fixture.userId && it.challengeId == CHALLENGE },
                any(),
            )
        }
        assertEquals(listOf(true), fixture.publishedInTransaction)
    }

    @Test
    fun `should still return the token when the confirmation can't be published`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.challengeStored(fixture.ownChallenge())
        fixture.telegramAccountOf(fixture.userId)
        coEvery { fixture.eventProducer.send(any<AuthEvent.TelegramActionConfirmed>(), any()) } throws
            IllegalStateException("outbox down")

        whenn()
        val result = fixture.sut(fixture.userId, fixture.launch("op_delete-account_$CHALLENGE"))

        then()
        val token = result.getOrThrow().token
        assertEquals("done", fixture.spend(token, ActionConfirmationGrant.accountDeletion(fixture.userId)).getOrThrow())
    }

    @Test
    fun `should publish nothing when the confirmation is refused`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.challengeStored(fixture.ownChallenge())
        fixture.telegramAccountOf(Uuid.random())

        whenn()
        val result = fixture.sut(fixture.userId, fixture.launch("op_delete-account_$CHALLENGE"))

        then()
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AuthEvent.TelegramActionConfirmed>(), any()) }
    }

    private companion object {
        const val BOT_ID = 7000000001L
        const val TELEGRAM_USER_ID = 123456789L
        const val CHALLENGE = "0123456789abcdef0123456789abcdef"
    }
}
