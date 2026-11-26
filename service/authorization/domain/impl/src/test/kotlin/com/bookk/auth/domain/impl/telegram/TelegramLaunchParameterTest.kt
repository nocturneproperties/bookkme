package com.bookk.auth.domain.impl.telegram

import com.bookk.auth.domain.api.actionconfirmation.entity.ConfirmableAction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class TelegramLaunchParameterTest {

    @Test
    fun `should name account deletion and personal data export by their kinds`() = runUnitTest {
        given()
        val actions = listOf(ConfirmableAction.DELETE_ACCOUNT, ConfirmableAction.EXPORT_PERSONAL_DATA)

        whenn()
        val parameters = actions.map { TelegramLaunchParameter.confirmation(it, CHALLENGE) }

        then()
        assertEquals(listOf("op_delete-account_$CHALLENGE", "op_export-personal_$CHALLENGE"), parameters)
    }

    @Test
    fun `should have no kind for the business app's actions`() = runUnitTest {
        given()
        val actions = listOf(ConfirmableAction.DELETE_CLIENT, ConfirmableAction.EXPORT_BUSINESS_DATA)

        whenn()
        val confirmable = actions.filter(TelegramLaunchParameter::isConfirmable)

        then()
        assertTrue(confirmable.isEmpty()) { confirmable.toString() }
    }

    @Test
    fun `should read back the action and the challenge of a confirmation launch`() = runUnitTest {
        given()
        val parameters = listOf("op_delete-account_$CHALLENGE", "op_export-personal_$CHALLENGE")

        whenn()
        val launches = parameters.map(TelegramLaunchParameter::parseConfirmation)

        then()
        assertEquals(
            listOf(
                TelegramConfirmationLaunch(ConfirmableAction.DELETE_ACCOUNT, CHALLENGE),
                TelegramConfirmationLaunch(ConfirmableAction.EXPORT_PERSONAL_DATA, CHALLENGE),
            ),
            launches,
        )
    }

    @Test
    fun `should not read a confirmation from any other launch`() = runUnitTest {
        given()
        val parameters = listOf(
            null,
            "",
            "anna-barber",
            Uuid.random().toString(),
            "op_delete-client_$CHALLENGE",
            "op_export-business_$CHALLENGE",
            "op_unknown_$CHALLENGE",
            "op_delete-account_${CHALLENGE.uppercase()}",
            "op_delete-account_${CHALLENGE.drop(1)}",
            "op_delete-account_${CHALLENGE}0",
            "op_delete-account_$CHALLENGE\n",
            "xop_delete-account_$CHALLENGE",
            "op_delete-account",
        )

        whenn()
        val read = parameters.filter { TelegramLaunchParameter.parseConfirmation(it) != null }

        then()
        assertTrue(read.isEmpty()) { read.toString() }
    }

    @Test
    fun `should create a new 32 character lowercase hex challenge every time`() = runUnitTest {
        given()
        val count = 100

        whenn()
        val challenges = List(count) { TelegramLaunchParameter.newChallengeId() }

        then()
        assertEquals(count, challenges.toSet().size)
        assertTrue(challenges.all { Regex("^[0-9a-f]{32}$").matches(it) }) { challenges.toString() }
    }

    @Test
    fun `should fit Telegram's limit on start parameters`() = runUnitTest {
        given()
        val telegramLimit = Regex("^[A-Za-z0-9_-]{1,512}$")

        whenn()
        val parameter = TelegramLaunchParameter.confirmation(ConfirmableAction.EXPORT_PERSONAL_DATA, CHALLENGE)

        then()
        assertTrue(telegramLimit.matches(parameter))
    }

    private companion object {
        const val CHALLENGE = "0123456789abcdef0123456789abcdef"
    }
}
