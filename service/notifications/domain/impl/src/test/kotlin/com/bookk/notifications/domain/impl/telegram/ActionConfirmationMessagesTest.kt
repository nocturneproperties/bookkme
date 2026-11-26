package com.bookk.notifications.domain.impl.telegram

import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.client.api.telegram.TelegramConfirmedAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ActionConfirmationMessagesTest {

    @Test
    fun `should ask to confirm an account deletion in every language`() = runUnitTest {
        given()
        val expected = mapOf(
            Language.EN to "You asked to delete your BookkMe account. Tap the button below and confirm in " +
                "the mini app within 5 minutes. If this wasn't you, ignore this message.",
            Language.UK to "Ви попросили видалити свій акаунт BookkMe. Натисніть кнопку нижче й підтвердьте " +
                "в міні-застосунку протягом 5 хвилин. Якщо це були не ви, просто проігноруйте це повідомлення.",
            Language.PL to "Otrzymaliśmy prośbę o usunięcie Twojego konta BookkMe. Naciśnij przycisk poniżej i " +
                "potwierdź w miniaplikacji w ciągu 5 minut. Jeśli to nie Ty, zignoruj tę wiadomość.",
        )

        whenn()
        val texts = Language.entries.associateWith {
            actionConfirmationText(TelegramConfirmedAction.DELETE_ACCOUNT, it)
        }

        then()
        assertEquals(expected, texts)
    }

    @Test
    fun `should ask to confirm a personal data export in every language`() = runUnitTest {
        given()
        val expected = mapOf(
            Language.EN to "You asked for a copy of your BookkMe data. Tap the button below and confirm in " +
                "the mini app within 5 minutes. If this wasn't you, ignore this message.",
            Language.UK to "Ви попросили копію своїх даних у BookkMe. Натисніть кнопку нижче й підтвердьте " +
                "в міні-застосунку протягом 5 хвилин. Якщо це були не ви, просто проігноруйте це повідомлення.",
            Language.PL to "Otrzymaliśmy prośbę o kopię Twoich danych w BookkMe. Naciśnij przycisk poniżej i " +
                "potwierdź w miniaplikacji w ciągu 5 minut. Jeśli to nie Ty, zignoruj tę wiadomość.",
        )

        whenn()
        val texts = Language.entries.associateWith {
            actionConfirmationText(TelegramConfirmedAction.EXPORT_PERSONAL_DATA, it)
        }

        then()
        assertEquals(expected, texts)
    }

    @Test
    fun `should label the button in every language`() = runUnitTest {
        given()
        val expected = mapOf(
            Language.EN to "Confirm in the mini app",
            Language.UK to "Підтвердити в міні-застосунку",
            Language.PL to "Potwierdź w miniaplikacji",
        )

        whenn()
        val labels = Language.entries.associateWith { actionConfirmationButton(it) }

        then()
        assertEquals(expected, labels)
    }

    @Test
    fun `should say the confirmation was used and the request continues in the app in every language`() =
        runUnitTest {
            given()
            val expected = mapOf(
                Language.EN to "Confirmed. Finish your request in the mini app.",
                Language.UK to "Підтверджено. Завершіть запит у міні-застосунку.",
                Language.PL to "Potwierdzono. Dokończ prośbę w miniaplikacji.",
            )

            whenn()
            val texts = Language.entries.associateWith { actionConfirmedText(it) }

            then()
            assertEquals(expected, texts)
        }
}
