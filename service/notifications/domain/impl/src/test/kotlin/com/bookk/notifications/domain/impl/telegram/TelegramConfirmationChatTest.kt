package com.bookk.notifications.domain.impl.telegram

import com.bookk.core.domain.datasource.transaction.mockedTransactionManager
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationDelivery.Outcome
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationMessage
import com.bookk.notifications.client.api.telegram.TelegramConfirmedAction
import com.bookk.notifications.domain.api.entity.PendingTelegramConfirmation
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import com.bookk.notifications.domain.datasource.TelegramConfirmationMessageDataSource
import com.bookk.notifications.domain.impl.channel.telegram.TelegramDelivery
import com.bookk.notifications.domain.impl.channel.telegram.TelegramMessenger
import com.bookk.notifications.domain.impl.channel.telegram.TelegramUrlButton
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.uuid.Uuid

internal class TelegramConfirmationChatTest {

    private class SutFixture(botUsername: String? = "bookkme_bot") {
        val userId: Uuid = Uuid.random()
        val transactionManager = mockedTransactionManager()
        val targetDataSource = mockk<NotificationTargetDataSource>()
        val pendingDataSource = mockk<TelegramConfirmationMessageDataSource>()
        val telegramMessenger = mockk<TelegramMessenger>()
        val sentText = slot<String>()
        val sentButton = slot<TelegramUrlButton>()
        val sut = TelegramConfirmationChat(
            transactionManager,
            targetDataSource,
            pendingDataSource,
            telegramMessenger,
            botUsername,
        )

        init {
            coEvery { pendingDataSource.get(userId) } returns null
            coEvery { pendingDataSource.save(userId, any()) } just runs
            coEvery { pendingDataSource.forget(userId, any()) } just runs
        }

        fun chat(chatId: String?) {
            coEvery { targetDataSource.getTelegram(userId) } returns chatId
        }

        fun telegramAnswers(delivery: TelegramDelivery) {
            coEvery { telegramMessenger.send(any(), capture(sentText), capture(sentButton)) } returns delivery
        }

        fun previousMessage(previous: PendingTelegramConfirmation) {
            coEvery { pendingDataSource.get(userId) } returns previous
        }

        fun rememberedForConfirmation(chatId: String? = "123456789") {
            coEvery { targetDataSource.getTelegram(userId) } returns chatId
            coEvery { pendingDataSource.get(userId) } returns REMEMBERED
            coEvery { telegramMessenger.edit(any(), any(), any()) } returns true
        }
    }

    @Test
    fun `should send the user a message with a button that opens the Mini App with the start parameter`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            fixture.chat("123456789")
            fixture.telegramAnswers(TelegramDelivery.Sent(MESSAGE_ID))
            val message = TelegramActionConfirmationMessage.stub(startParameter = "op_delete-account_$CHALLENGE")

            whenn()
            val result = fixture.sut(fixture.userId, message)

            then()
            assertEquals(Outcome.SENT, result.getOrThrow().outcome)
            coVerify(exactly = 1) { fixture.telegramMessenger.send(123456789, any(), any()) }
            assertEquals(
                "https://t.me/bookkme_bot?startapp=op_delete-account_$CHALLENGE",
                fixture.sentButton.captured.url,
            )
        }

    @Test
    fun `should write the message and the button in the requested language`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.chat("123456789")
        fixture.telegramAnswers(TelegramDelivery.Sent(MESSAGE_ID))

        whenn()
        fixture.sut(fixture.userId, TelegramActionConfirmationMessage.stub(language = Language.UK))

        then()
        assertEquals(
            actionConfirmationText(TelegramConfirmedAction.DELETE_ACCOUNT, Language.UK),
            fixture.sentText.captured,
        )
        assertEquals(actionConfirmationButton(Language.UK), fixture.sentButton.captured.text)
    }

    @Test
    fun `should name the action being confirmed in the message`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.chat("123456789")
        fixture.telegramAnswers(TelegramDelivery.Sent(MESSAGE_ID))

        whenn()
        fixture.sut(
            fixture.userId,
            TelegramActionConfirmationMessage.stub(TelegramConfirmedAction.EXPORT_PERSONAL_DATA),
        )

        then()
        assertEquals(
            actionConfirmationText(TelegramConfirmedAction.EXPORT_PERSONAL_DATA, Language.EN),
            fixture.sentText.captured,
        )
    }

    @Test
    fun `should report the chat unreachable when Telegram forbids the bot to write`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.chat("123456789")
        fixture.telegramAnswers(TelegramDelivery.ChatUnreachable)

        whenn()
        val result = fixture.sut(fixture.userId, TelegramActionConfirmationMessage.stub())

        then()
        assertEquals(Outcome.CHAT_UNREACHABLE, result.getOrThrow().outcome)
    }

    @Test
    fun `should report no chat when notifications has no Telegram user id for the user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.chat(null)

        whenn()
        val result = fixture.sut(fixture.userId, TelegramActionConfirmationMessage.stub())

        then()
        assertEquals(Outcome.NO_CHAT, result.getOrThrow().outcome)
        coVerify(exactly = 0) { fixture.telegramMessenger.send(any(), any(), any()) }
    }

    @Test
    fun `should report unavailable without sending when the bot username is not set`() = runUnitTest {
        given()
        val fixture = SutFixture(botUsername = null)
        fixture.chat("123456789")

        whenn()
        val result = fixture.sut(fixture.userId, TelegramActionConfirmationMessage.stub())

        then()
        assertEquals(Outcome.UNAVAILABLE, result.getOrThrow().outcome)
        coVerify(exactly = 0) { fixture.telegramMessenger.send(any(), any(), any()) }
    }

    @Test
    fun `should report unavailable when no bot token is set and the message is dropped`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.chat("123456789")
        fixture.telegramAnswers(TelegramDelivery.Dropped)

        whenn()
        val result = fixture.sut(fixture.userId, TelegramActionConfirmationMessage.stub())

        then()
        assertEquals(Outcome.UNAVAILABLE, result.getOrThrow().outcome)
    }

    @Test
    fun `should report failed instead of failing when the Bot API can't be reached`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.chat("123456789")
        coEvery { fixture.telegramMessenger.send(any(), any(), any()) } throws IOException("timeout")

        whenn()
        val result = fixture.sut(fixture.userId, TelegramActionConfirmationMessage.stub())

        then()
        assertEquals(Outcome.FAILED, result.getOrThrow().outcome)
    }

    @Test
    fun `should remember the sent message so it can be marked confirmed in the user's language`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.chat("123456789")
        fixture.telegramAnswers(TelegramDelivery.Sent(MESSAGE_ID))
        val message = TelegramActionConfirmationMessage.stub(challengeId = CHALLENGE, language = Language.UK)

        whenn()
        fixture.sut(fixture.userId, message)

        then()
        val expected = PendingTelegramConfirmation(
            challengeId = CHALLENGE,
            messageId = MESSAGE_ID,
            confirmedText = actionConfirmedText(Language.UK),
        )
        coVerify(exactly = 1) { fixture.pendingDataSource.save(fixture.userId, expected) }
    }

    @Test
    fun `should remember nothing when the message was not sent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.chat("123456789")
        fixture.telegramAnswers(TelegramDelivery.ChatUnreachable)

        whenn()
        fixture.sut(fixture.userId, TelegramActionConfirmationMessage.stub())

        then()
        coVerify(exactly = 0) { fixture.pendingDataSource.save(any(), any()) }
    }

    @Test
    fun `should delete the previous unconfirmed message once the new one is sent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.chat("123456789")
        fixture.previousMessage(PendingTelegramConfirmation.stub(messageId = 5))
        fixture.telegramAnswers(TelegramDelivery.Sent(MESSAGE_ID))
        coEvery { fixture.telegramMessenger.delete(123456789, 5) } returns true

        whenn()
        val result = fixture.sut(fixture.userId, TelegramActionConfirmationMessage.stub())

        then()
        assertEquals(Outcome.SENT, result.getOrThrow().outcome)
        coVerifyOrder {
            fixture.telegramMessenger.send(123456789, any(), any())
            fixture.telegramMessenger.delete(123456789, 5)
            fixture.pendingDataSource.save(fixture.userId, match { it.messageId == MESSAGE_ID })
        }
    }

    @Test
    fun `should keep the previous message when the new one is not sent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.chat("123456789")
        fixture.previousMessage(PendingTelegramConfirmation.stub(messageId = 5))
        fixture.telegramAnswers(TelegramDelivery.ChatUnreachable)

        whenn()
        fixture.sut(fixture.userId, TelegramActionConfirmationMessage.stub())

        then()
        coVerify(exactly = 0) { fixture.telegramMessenger.delete(any(), any()) }
        coVerify(exactly = 0) { fixture.pendingDataSource.forget(any(), any()) }
    }

    @Test
    fun `should forget the deleted previous message when the new one has no message id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val previous = PendingTelegramConfirmation.stub(messageId = 5)
        fixture.chat("123456789")
        fixture.previousMessage(previous)
        fixture.telegramAnswers(TelegramDelivery.Sent(messageId = null))
        coEvery { fixture.telegramMessenger.delete(123456789, 5) } returns true

        whenn()
        fixture.sut(fixture.userId, TelegramActionConfirmationMessage.stub())

        then()
        coVerify(exactly = 1) { fixture.pendingDataSource.forget(fixture.userId, previous) }
        coVerify(exactly = 0) { fixture.pendingDataSource.save(any(), any()) }
    }

    @Test
    fun `should still remember the new message when the previous one can't be deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.chat("123456789")
        fixture.previousMessage(PendingTelegramConfirmation.stub(messageId = 5))
        fixture.telegramAnswers(TelegramDelivery.Sent(MESSAGE_ID))
        coEvery { fixture.telegramMessenger.delete(any(), any()) } throws IOException("timeout")

        whenn()
        val result = fixture.sut(fixture.userId, TelegramActionConfirmationMessage.stub())

        then()
        assertEquals(Outcome.SENT, result.getOrThrow().outcome)
        coVerify(exactly = 1) { fixture.pendingDataSource.save(fixture.userId, any()) }
    }

    @Test
    fun `should still send when the previous message can't be looked up`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.chat("123456789")
        coEvery { fixture.pendingDataSource.get(fixture.userId) } throws IOException("cache down")
        fixture.telegramAnswers(TelegramDelivery.Sent(MESSAGE_ID))

        whenn()
        val result = fixture.sut(fixture.userId, TelegramActionConfirmationMessage.stub())

        then()
        assertEquals(Outcome.SENT, result.getOrThrow().outcome)
        coVerify(exactly = 0) { fixture.telegramMessenger.delete(any(), any()) }
    }

    @Test
    fun `should still report the message sent when it can't be remembered`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.chat("123456789")
        fixture.telegramAnswers(TelegramDelivery.Sent(MESSAGE_ID))
        coEvery { fixture.pendingDataSource.save(any(), any()) } throws IOException("cache down")

        whenn()
        val result = fixture.sut(fixture.userId, TelegramActionConfirmationMessage.stub())

        then()
        assertEquals(Outcome.SENT, result.getOrThrow().outcome)
    }

    @Test
    fun `should edit the confirmed message and forget it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.rememberedForConfirmation()

        whenn()
        val result = fixture.sut.markConfirmed(fixture.userId, CHALLENGE)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.telegramMessenger.edit(123456789, 77, "Done") }
        coVerify(exactly = 1) { fixture.pendingDataSource.forget(fixture.userId, REMEMBERED) }
    }

    @Test
    fun `should leave a newer confirmation message alone when an older challenge is confirmed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.rememberedForConfirmation()

        whenn()
        val result = fixture.sut.markConfirmed(fixture.userId, "ffffffffffffffffffffffffffffffff")

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.telegramMessenger.edit(any(), any(), any()) }
        coVerify(exactly = 0) { fixture.pendingDataSource.forget(any(), any()) }
    }

    @Test
    fun `should do nothing when no confirmation message is remembered`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.rememberedForConfirmation()
        coEvery { fixture.pendingDataSource.get(fixture.userId) } returns null

        whenn()
        val result = fixture.sut.markConfirmed(fixture.userId, CHALLENGE)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.telegramMessenger.edit(any(), any(), any()) }
    }

    @Test
    fun `should still forget the message when Telegram can't edit it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.rememberedForConfirmation()
        coEvery { fixture.telegramMessenger.edit(any(), any(), any()) } throws IOException("timeout")

        whenn()
        val result = fixture.sut.markConfirmed(fixture.userId, CHALLENGE)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.pendingDataSource.forget(fixture.userId, REMEMBERED) }
    }

    @Test
    fun `should forget the message without editing when the user has no Telegram chat any more`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.rememberedForConfirmation(chatId = null)

        whenn()
        val result = fixture.sut.markConfirmed(fixture.userId, CHALLENGE)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.telegramMessenger.edit(any(), any(), any()) }
        coVerify(exactly = 1) { fixture.pendingDataSource.forget(fixture.userId, REMEMBERED) }
    }

    @Test
    fun `should fail so the event is retried when the remembered message can't be read`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.rememberedForConfirmation()
        coEvery { fixture.pendingDataSource.get(fixture.userId) } throws IOException("cache down")

        whenn()
        val result = fixture.sut.markConfirmed(fixture.userId, CHALLENGE)

        then()
        assertTrue(result.isFailure)
    }

    private companion object {
        const val CHALLENGE = "0123456789abcdef0123456789abcdef"
        const val MESSAGE_ID = 77L
        val REMEMBERED = PendingTelegramConfirmation.stub(
            challengeId = CHALLENGE,
            messageId = 77,
            confirmedText = "Done",
        )
    }
}
