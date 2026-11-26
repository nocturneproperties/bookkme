package com.bookk.auth.domain.impl.operation.telegram

import com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationToken
import com.bookk.auth.domain.api.telegram.entity.TelegramActionConfirmationRequest
import com.bookk.auth.domain.api.telegram.operation.ConfirmActionWithTelegram
import com.bookk.auth.domain.api.telegram.operation.ConfirmActionWithTelegram.Error.TelegramAccountMismatch
import com.bookk.auth.domain.api.telegram.operation.ConfirmActionWithTelegram.Error.TelegramConfirmationExpired
import com.bookk.auth.domain.api.telegram.operation.TelegramConfirmationUnavailable
import com.bookk.auth.domain.datasource.TelegramConfirmationDataSource
import com.bookk.auth.domain.datasource.TelegramIdentityDataSource
import com.bookk.auth.domain.impl.operation.actionconfirmation.issueActionConfirmation
import com.bookk.auth.domain.impl.telegram.TelegramConfirmationLaunch
import com.bookk.auth.domain.impl.telegram.TelegramInitDataVerifier
import com.bookk.auth.domain.impl.telegram.TelegramLaunchParameter
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.auth.client.AuthEvent
import library.signing.TokenIssuer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class ConfirmActionWithTelegramImpl(
    private val verifier: TelegramInitDataVerifier,
    private val confirmationDataSource: TelegramConfirmationDataSource,
    private val telegramIdentityDataSource: TelegramIdentityDataSource,
    private val tokenIssuer: TokenIssuer,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
) : ConfirmActionWithTelegram {

    override suspend fun invoke(
        userId: Uuid,
        request: TelegramActionConfirmationRequest,
    ): Result<ActionConfirmationToken> {
        if (!verifier.isConfigured) return Result.failure(TelegramConfirmationUnavailable())
        return runSuspendCatching {
            val launch = verifier.verifyLaunch(request.initData, LAUNCH_MAX_AGE)
            val confirmation = TelegramLaunchParameter.parseConfirmation(launch.startParam)
                ?: throw TelegramConfirmationExpired()
            requireOwnChallenge(userId, confirmation)
            requireLinkedTelegramAccount(userId, launch.user.id)
            val challenge = confirmationDataSource.takeChallenge(confirmation.challengeId)
                ?: throw TelegramConfirmationExpired()
            tokenIssuer.issueActionConfirmation(userId, challenge.action, challenge.businessId, challenge.targetId)
                .also { publishConfirmed(userId, confirmation.challengeId) }
        }
    }

    private suspend fun publishConfirmed(userId: Uuid, challengeId: String) {
        runSuspendCatching {
            transactionManager.transactionOrThrow {
                eventProducer.send(AuthEvent.TelegramActionConfirmed(userId = userId, challengeId = challengeId))
            }
        }.onFailure { logger.warn("Failed to publish the Telegram action confirmation of user {}", userId, it) }
    }

    private suspend fun requireOwnChallenge(userId: Uuid, confirmation: TelegramConfirmationLaunch) {
        val challenge = confirmationDataSource.getChallenge(confirmation.challengeId)
            ?: throw TelegramConfirmationExpired()
        if (challenge.userId != userId) throw TelegramConfirmationExpired()
        if (challenge.action != confirmation.action) throw TelegramConfirmationExpired()
    }

    private suspend fun requireLinkedTelegramAccount(userId: Uuid, telegramUserId: Long) {
        val account = transactionManager.transactionOrThrow {
            telegramIdentityDataSource.getAccountByTelegramUser(telegramUserId)
        }
        if (account?.userId != userId) throw TelegramAccountMismatch()
    }

    private companion object {
        val LAUNCH_MAX_AGE = 5.minutes
        val logger: Logger = LoggerFactory.getLogger(ConfirmActionWithTelegramImpl::class.java)
    }
}
