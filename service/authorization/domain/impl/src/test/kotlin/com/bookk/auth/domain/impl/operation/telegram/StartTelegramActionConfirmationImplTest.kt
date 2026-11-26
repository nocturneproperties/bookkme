package com.bookk.auth.domain.impl.operation.telegram

import com.bookk.auth.domain.api.actionconfirmation.entity.ConfirmableAction
import com.bookk.auth.domain.api.telegram.entity.TelegramActionConfirmationStart
import com.bookk.auth.domain.api.telegram.entity.TelegramConfirmationChallenge
import com.bookk.auth.domain.api.telegram.operation.StartTelegramActionConfirmation.Error
import com.bookk.auth.domain.api.telegram.operation.TelegramConfirmationUnavailable
import com.bookk.auth.domain.datasource.TelegramConfirmationDataSource
import com.bookk.auth.domain.datasource.TelegramIdentityDataSource
import com.bookk.auth.domain.impl.telegram.TelegramInitDataVerifier
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.client.api.telegram.SendTelegramActionConfirmation
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationDelivery
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationDelivery.Outcome
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationMessage
import com.bookk.notifications.client.api.telegram.TelegramConfirmedAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.uuid.Uuid

internal class StartTelegramActionConfirmationImplTest {

    private class SutFixture {
        val userId: Uuid = Uuid.random()
        val telegramIdentityDataSource = mockk<TelegramIdentityDataSource>()
        val confirmationDataSource = mockk<TelegramConfirmationDataSource>()
        val sendTelegramActionConfirmation = mockk<SendTelegramActionConfirmation>()
        val transactionManager = mockk<TransactionManager>()
        val verifier = mockk<TelegramInitDataVerifier> { every { isConfigured } returns true }
        val savedChallengeId = slot<String>()
        val savedChallenge = slot<TelegramConfirmationChallenge>()
        val sentMessage = slot<TelegramActionConfirmationMessage>()
        val sut = StartTelegramActionConfirmationImpl(
            telegramIdentityDataSource,
            confirmationDataSource,
            sendTelegramActionConfirmation,
            verifier,
            transactionManager,
        )

        fun ownRequest(action: ConfirmableAction = ConfirmableAction.DELETE_ACCOUNT): TelegramActionConfirmationStart {
            return TelegramActionConfirmationStart(action = action, businessId = Uuid.NIL, targetId = userId)
        }

        fun linkedToTelegram(linked: Boolean = true) {
            transactionManager.mockTransaction()
            coEvery { telegramIdentityDataSource.hasIdentity(userId) } returns linked
            coEvery { confirmationDataSource.claimStartCooldown(userId) } returns true
            coEvery { confirmationDataSource.releaseStartCooldown(userId) } just runs
            coEvery { confirmationDataSource.saveChallenge(capture(savedChallengeId), capture(savedChallenge)) } just
                runs
            coEvery { confirmationDataSource.deleteChallenge(any()) } just runs
        }

        fun notificationsAnswer(outcome: Outcome) {
            coEvery { sendTelegramActionConfirmation(userId, capture(sentMessage)) } returns
                Result.success(TelegramActionConfirmationDelivery(outcome))
        }
    }

    @Test
    fun `should send an account deletion confirmation link to the user's Telegram chat`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.linkedToTelegram()
        fixture.notificationsAnswer(Outcome.SENT)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.ownRequest(ConfirmableAction.DELETE_ACCOUNT), Language.UK)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        val challengeId = fixture.savedChallengeId.captured
        assertTrue(Regex("^[0-9a-f]{32}$").matches(challengeId)) { challengeId }
        assertEquals(
            TelegramActionConfirmationMessage(
                action = TelegramConfirmedAction.DELETE_ACCOUNT,
                startParameter = "op_delete-account_$challengeId",
                language = Language.UK,
            ),
            fixture.sentMessage.captured,
        )
        coVerify(exactly = 0) { fixture.confirmationDataSource.releaseStartCooldown(any()) }
        coVerify(exactly = 0) { fixture.confirmationDataSource.deleteChallenge(any()) }
    }

    @Test
    fun `should send a personal data export confirmation link to the user's Telegram chat`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.linkedToTelegram()
        fixture.notificationsAnswer(Outcome.SENT)

        whenn()
        val result = fixture.sut(
            fixture.userId,
            fixture.ownRequest(ConfirmableAction.EXPORT_PERSONAL_DATA),
            Language.EN,
        )

        then()
        assertTrue(result.isSuccess) { result.toString() }
        assertEquals(TelegramConfirmedAction.EXPORT_PERSONAL_DATA, fixture.sentMessage.captured.action)
        assertEquals(
            "op_export-personal_${fixture.savedChallengeId.captured}",
            fixture.sentMessage.captured.startParameter,
        )
    }

    @Test
    fun `should remember who confirms which action on which target with the challenge`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.linkedToTelegram()
        fixture.notificationsAnswer(Outcome.SENT)

        whenn()
        fixture.sut(fixture.userId, fixture.ownRequest(ConfirmableAction.EXPORT_PERSONAL_DATA), Language.EN)

        then()
        assertEquals(
            TelegramConfirmationChallenge(
                userId = fixture.userId,
                action = ConfirmableAction.EXPORT_PERSONAL_DATA,
                businessId = Uuid.NIL,
                targetId = fixture.userId,
            ),
            fixture.savedChallenge.captured,
        )
    }

    @Test
    fun `should reject the business app's actions without reading anything`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requests = listOf(
            fixture.ownRequest(ConfirmableAction.DELETE_CLIENT),
            fixture.ownRequest(ConfirmableAction.EXPORT_BUSINESS_DATA),
        )

        whenn()
        val results = requests.map { fixture.sut(fixture.userId, it, Language.EN) }

        then()
        assertTrue(results.all { it.exceptionOrNull() is Error.ActionNotConfirmableThroughTelegram }) {
            results.toString()
        }
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
        coVerify(exactly = 0) { fixture.confirmationDataSource.claimStartCooldown(any()) }
    }

    @Test
    fun `should reject a business id other than the nil id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.ownRequest().copy(businessId = Uuid.random())

        whenn()
        val result = fixture.sut(fixture.userId, request, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is Error.ActionNotConfirmableThroughTelegram) { result.toString() }
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should reject a target other than the caller`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.ownRequest().copy(targetId = Uuid.random())

        whenn()
        val result = fixture.sut(fixture.userId, request, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is Error.ActionNotConfirmableThroughTelegram) { result.toString() }
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should reject a caller without a Telegram identity`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.linkedToTelegram(linked = false)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.ownRequest(), Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is Error.TelegramIdentityMissing) { result.toString() }
        coVerify(exactly = 0) { fixture.confirmationDataSource.claimStartCooldown(any()) }
        coVerify(exactly = 0) { fixture.sendTelegramActionConfirmation(any(), any()) }
    }

    @Test
    fun `should reject a second start within the cooldown without sending another message`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.linkedToTelegram()
        coEvery { fixture.confirmationDataSource.claimStartCooldown(fixture.userId) } returns false

        whenn()
        val result = fixture.sut(fixture.userId, fixture.ownRequest(), Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is Error.TelegramConfirmationTooFrequent) { result.toString() }
        coVerify(exactly = 0) { fixture.sendTelegramActionConfirmation(any(), any()) }
        coVerify(exactly = 0) { fixture.confirmationDataSource.saveChallenge(any(), any()) }
        coVerify(exactly = 0) { fixture.confirmationDataSource.releaseStartCooldown(any()) }
    }

    @Test
    fun `should tell the bot can't write and give back the cooldown and the challenge`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.linkedToTelegram()
        fixture.notificationsAnswer(Outcome.CHAT_UNREACHABLE)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.ownRequest(), Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is Error.TelegramChatUnreachable) { result.toString() }
        coVerify(exactly = 1) { fixture.confirmationDataSource.releaseStartCooldown(fixture.userId) }
        coVerify(exactly = 1) { fixture.confirmationDataSource.deleteChallenge(fixture.savedChallengeId.captured) }
    }

    @Test
    fun `should answer unavailable, to retry later, while notifications has no Telegram chat for the user`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            fixture.linkedToTelegram()
            fixture.notificationsAnswer(Outcome.NO_CHAT)

            whenn()
            val result = fixture.sut(fixture.userId, fixture.ownRequest(), Language.EN)

            then()
            assertTrue(result.exceptionOrNull() is TelegramConfirmationUnavailable) { result.toString() }
            coVerify(exactly = 1) { fixture.confirmationDataSource.releaseStartCooldown(fixture.userId) }
            coVerify(exactly = 1) {
                fixture.confirmationDataSource.deleteChallenge(fixture.savedChallengeId.captured)
            }
        }

    @Test
    fun `should answer unavailable when the bot failed to send and give back the cooldown`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.linkedToTelegram()
        fixture.notificationsAnswer(Outcome.FAILED)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.ownRequest(), Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is TelegramConfirmationUnavailable) { result.toString() }
        coVerify(exactly = 1) { fixture.confirmationDataSource.releaseStartCooldown(fixture.userId) }
        coVerify(exactly = 1) { fixture.confirmationDataSource.deleteChallenge(fixture.savedChallengeId.captured) }
    }

    @Test
    fun `should answer unavailable when the bot is not set up and give back the cooldown`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.linkedToTelegram()
        fixture.notificationsAnswer(Outcome.UNAVAILABLE)

        whenn()
        val result = fixture.sut(fixture.userId, fixture.ownRequest(), Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is TelegramConfirmationUnavailable) { result.toString() }
        coVerify(exactly = 1) { fixture.confirmationDataSource.releaseStartCooldown(fixture.userId) }
        coVerify(exactly = 1) { fixture.confirmationDataSource.deleteChallenge(fixture.savedChallengeId.captured) }
    }

    @Test
    fun `should answer unavailable without reading anything when no bot id is set`() = runUnitTest {
        given()
        val fixture = SutFixture()
        every { fixture.verifier.isConfigured } returns false

        whenn()
        val result = fixture.sut(fixture.userId, fixture.ownRequest(), Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is TelegramConfirmationUnavailable) { result.toString() }
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
        coVerify(exactly = 0) { fixture.confirmationDataSource.claimStartCooldown(any()) }
        coVerify(exactly = 0) { fixture.sendTelegramActionConfirmation(any(), any()) }
    }

    @Test
    fun `should give back the cooldown and the challenge when notifications can't be reached`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.linkedToTelegram()
        coEvery { fixture.sendTelegramActionConfirmation(fixture.userId, any()) } returns
            Result.failure(IOException("connection refused"))

        whenn()
        val result = fixture.sut(fixture.userId, fixture.ownRequest(), Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is IOException) { result.toString() }
        coVerify(exactly = 1) { fixture.confirmationDataSource.releaseStartCooldown(fixture.userId) }
        coVerify(exactly = 1) { fixture.confirmationDataSource.deleteChallenge(fixture.savedChallengeId.captured) }
    }
}
