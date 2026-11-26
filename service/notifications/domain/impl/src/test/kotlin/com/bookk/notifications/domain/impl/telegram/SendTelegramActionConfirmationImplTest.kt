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
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import com.bookk.notifications.domain.impl.channel.telegram.TelegramDelivery
import com.bookk.notifications.domain.impl.channel.telegram.TelegramMessenger
import com.bookk.notifications.domain.impl.channel.telegram.TelegramMiniAppLink
import com.bookk.notifications.domain.impl.channel.telegram.TelegramUrlButton
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.uuid.Uuid

internal class SendTelegramActionConfirmationImplTest {

    private class SutFixture(miniAppLink: TelegramMiniAppLink? = TelegramMiniAppLink("bookkme_bot")) {
        val userId: Uuid = Uuid.random()
        val transactionManager = mockedTransactionManager()
        val targetDataSource = mockk<NotificationTargetDataSource>()
        val telegramMessenger = mockk<TelegramMessenger>()
        val sentText = slot<String>()
        val sentButton = slot<TelegramUrlButton>()
        val sut =
            SendTelegramActionConfirmationImpl(transactionManager, targetDataSource, telegramMessenger, miniAppLink)

        fun chat(chatId: String?) {
            coEvery { targetDataSource.getTelegram(userId) } returns chatId
        }

        fun telegramAnswers(delivery: TelegramDelivery) {
            coEvery { telegramMessenger.send(any(), capture(sentText), capture(sentButton)) } returns delivery
        }
    }

    @Test
    fun `should send the user a message with a button that opens the Mini App with the start parameter`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            fixture.chat("123456789")
            fixture.telegramAnswers(TelegramDelivery.SENT)
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
        fixture.telegramAnswers(TelegramDelivery.SENT)

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
        fixture.telegramAnswers(TelegramDelivery.SENT)

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
        fixture.telegramAnswers(TelegramDelivery.CHAT_UNREACHABLE)

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
        val fixture = SutFixture(miniAppLink = null)
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
        fixture.telegramAnswers(TelegramDelivery.DROPPED)

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

    private companion object {
        const val CHALLENGE = "0123456789abcdef0123456789abcdef"
    }
}
