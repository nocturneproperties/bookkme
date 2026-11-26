package com.bookk.auth.domain.impl.telegram

import com.bookk.auth.domain.api.actionconfirmation.entity.ConfirmableAction
import java.security.SecureRandom

internal data class TelegramConfirmationLaunch(
    val action: ConfirmableAction,
    val challengeId: String,
)

internal object TelegramLaunchParameter {
    private const val OPERATION_PREFIX = "op_"
    private const val CHALLENGE_BYTES = 16

    private val confirmationKinds = mapOf(
        ConfirmableAction.DELETE_ACCOUNT to "delete-account",
        ConfirmableAction.EXPORT_PERSONAL_DATA to "export-personal",
    )
    private val actionsByKind = confirmationKinds.entries.associate { (action, kind) -> kind to action }
    private val confirmationPattern = Regex(
        "^$OPERATION_PREFIX(${confirmationKinds.values.joinToString("|")})_([0-9a-f]{32})$",
    )
    private val random = SecureRandom()

    fun isConfirmable(action: ConfirmableAction): Boolean {
        return action in confirmationKinds
    }

    fun confirmation(action: ConfirmableAction, challengeId: String): String {
        val kind = requireNotNull(confirmationKinds[action]) { "$action is not confirmable through Telegram" }
        return "$OPERATION_PREFIX${kind}_$challengeId"
    }

    fun parseConfirmation(startParam: String?): TelegramConfirmationLaunch? {
        val match = startParam?.let(confirmationPattern::matchEntire) ?: return null
        val (kind, challengeId) = match.destructured
        return TelegramConfirmationLaunch(actionsByKind.getValue(kind), challengeId)
    }

    fun newChallengeId(): String {
        val bytes = ByteArray(CHALLENGE_BYTES).also(random::nextBytes)
        return bytes.toHexString()
    }
}
