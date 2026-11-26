package com.bookk.auth.domain.impl.operation.telegram

import com.bookk.auth.domain.api.actionconfirmation.entity.ConfirmableAction
import com.bookk.auth.domain.api.telegram.entity.TelegramActionConfirmationStart
import com.bookk.auth.domain.api.telegram.entity.TelegramConfirmationChallenge
import com.bookk.auth.domain.api.telegram.operation.StartTelegramActionConfirmation
import com.bookk.auth.domain.api.telegram.operation.StartTelegramActionConfirmation.Error
import com.bookk.auth.domain.api.telegram.operation.TelegramConfirmationUnavailable
import com.bookk.auth.domain.datasource.TelegramConfirmationDataSource
import com.bookk.auth.domain.datasource.TelegramIdentityDataSource
import com.bookk.auth.domain.impl.telegram.TelegramInitDataVerifier
import com.bookk.auth.domain.impl.telegram.TelegramLaunchParameter
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.runCleaningUpOnFailure
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.notifications.client.api.telegram.SendTelegramActionConfirmation
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationDelivery.Outcome
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationMessage
import com.bookk.notifications.client.api.telegram.TelegramConfirmedAction
import kotlin.uuid.Uuid

internal class StartTelegramActionConfirmationImpl(
    private val telegramIdentityDataSource: TelegramIdentityDataSource,
    private val confirmationDataSource: TelegramConfirmationDataSource,
    private val sendTelegramActionConfirmation: SendTelegramActionConfirmation,
    private val verifier: TelegramInitDataVerifier,
    private val transactionManager: TransactionManager,
) : StartTelegramActionConfirmation {

    override suspend fun invoke(
        userId: Uuid,
        request: TelegramActionConfirmationStart,
        language: Language,
    ): Result<Unit> {
        if (!isOwnPersonalAction(userId, request)) return Result.failure(Error.ActionNotConfirmableThroughTelegram())
        if (!verifier.isConfigured) return Result.failure(TelegramConfirmationUnavailable())
        return runSuspendCatching {
            val linked = transactionManager.transactionOrThrow { telegramIdentityDataSource.hasIdentity(userId) }
            if (!linked) throw Error.TelegramIdentityMissing()
            if (!confirmationDataSource.claimStartCooldown(userId)) throw Error.TelegramConfirmationTooFrequent()
            val challengeId = TelegramLaunchParameter.newChallengeId()
            runCleaningUpOnFailure(cleanUp = { giveBack(userId, challengeId) }) {
                confirmationDataSource.saveChallenge(challengeId, challengeFor(userId, request))
                send(userId, request.action, challengeId, language)
            }
        }
    }

    private fun isOwnPersonalAction(userId: Uuid, request: TelegramActionConfirmationStart): Boolean {
        val confirmable = TelegramLaunchParameter.isConfirmable(request.action)
        return confirmable && request.businessId == Uuid.NIL && request.targetId == userId
    }

    private fun challengeFor(userId: Uuid, request: TelegramActionConfirmationStart): TelegramConfirmationChallenge {
        return TelegramConfirmationChallenge(
            userId = userId,
            action = request.action,
            businessId = request.businessId,
            targetId = request.targetId,
        )
    }

    private suspend fun send(userId: Uuid, action: ConfirmableAction, challengeId: String, language: Language) {
        val message = TelegramActionConfirmationMessage(
            action = action.toTelegramConfirmedAction(),
            startParameter = TelegramLaunchParameter.confirmation(action, challengeId),
            language = language,
        )
        when (sendTelegramActionConfirmation(userId, message).getOrThrow().outcome) {
            Outcome.SENT -> Unit
            Outcome.CHAT_UNREACHABLE -> throw Error.TelegramChatUnreachable()
            Outcome.NO_CHAT, Outcome.UNAVAILABLE, Outcome.FAILED -> throw TelegramConfirmationUnavailable()
        }
    }

    private suspend fun giveBack(userId: Uuid, challengeId: String) {
        confirmationDataSource.deleteChallenge(challengeId)
        confirmationDataSource.releaseStartCooldown(userId)
    }

    private fun ConfirmableAction.toTelegramConfirmedAction(): TelegramConfirmedAction {
        return when (this) {
            ConfirmableAction.DELETE_ACCOUNT -> TelegramConfirmedAction.DELETE_ACCOUNT

            ConfirmableAction.EXPORT_PERSONAL_DATA -> TelegramConfirmedAction.EXPORT_PERSONAL_DATA

            ConfirmableAction.DELETE_CLIENT, ConfirmableAction.EXPORT_BUSINESS_DATA ->
                throw Error.ActionNotConfirmableThroughTelegram()
        }
    }
}
