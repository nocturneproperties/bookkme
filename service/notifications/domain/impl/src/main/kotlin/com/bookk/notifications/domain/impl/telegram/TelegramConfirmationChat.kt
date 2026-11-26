package com.bookk.notifications.domain.impl.telegram

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.notifications.client.api.telegram.SendTelegramActionConfirmation
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationDelivery
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationDelivery.Outcome
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationMessage
import com.bookk.notifications.domain.api.entity.PendingTelegramConfirmation
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import com.bookk.notifications.domain.datasource.TelegramConfirmationMessageDataSource
import com.bookk.notifications.domain.impl.channel.telegram.TelegramDelivery
import com.bookk.notifications.domain.impl.channel.telegram.TelegramMessenger
import com.bookk.notifications.domain.impl.channel.telegram.TelegramMiniAppLink
import com.bookk.notifications.domain.impl.channel.telegram.TelegramUrlButton
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

internal class TelegramConfirmationChat(
    private val transactionManager: TransactionManager,
    private val targetDataSource: NotificationTargetDataSource,
    private val pendingDataSource: TelegramConfirmationMessageDataSource,
    private val telegramMessenger: TelegramMessenger,
    private val miniAppLink: TelegramMiniAppLink?,
) : SendTelegramActionConfirmation {

    override suspend fun invoke(
        userId: Uuid,
        message: TelegramActionConfirmationMessage,
    ): Result<TelegramActionConfirmationDelivery> = runSuspendCatching {
        TelegramActionConfirmationDelivery(send(userId, message))
    }

    suspend fun markConfirmed(userId: Uuid, challengeId: String): Result<Unit> = runSuspendCatching {
        val pending = pendingDataSource.get(userId)
        if (pending == null || pending.challengeId != challengeId) return@runSuspendCatching
        val chatId = chatOf(userId)
        if (chatId != null) {
            bestEffort(userId, "mark the Telegram action confirmation confirmed") {
                telegramMessenger.edit(chatId, pending.messageId, pending.confirmedText)
            }
        }
        pendingDataSource.forget(userId, pending)
    }

    private suspend fun send(userId: Uuid, message: TelegramActionConfirmationMessage): Outcome {
        val link = miniAppLink ?: return Outcome.UNAVAILABLE
        val chatId = chatOf(userId) ?: return Outcome.NO_CHAT
        val previous = bestEffort(userId, "look up the previous Telegram action confirmation") {
            pendingDataSource.get(userId)
        }
        val button = TelegramUrlButton(
            text = actionConfirmationButton(message.language),
            url = link.launching(message.startParameter),
        )
        val text = actionConfirmationText(message.action, message.language)
        val delivery = bestEffort(userId, "send a Telegram action confirmation") {
            telegramMessenger.send(chatId, text, button)
        }
        return when (delivery) {
            is TelegramDelivery.Sent -> {
                replace(userId, chatId, previous, delivery.messageId, message)
                Outcome.SENT
            }

            TelegramDelivery.ChatUnreachable -> Outcome.CHAT_UNREACHABLE

            TelegramDelivery.Dropped -> Outcome.UNAVAILABLE

            null -> Outcome.FAILED
        }
    }

    private suspend fun replace(
        userId: Uuid,
        chatId: Long,
        previous: PendingTelegramConfirmation?,
        messageId: Long?,
        message: TelegramActionConfirmationMessage,
    ) {
        if (previous != null) {
            bestEffort(userId, "delete the previous Telegram action confirmation") {
                telegramMessenger.delete(chatId, previous.messageId)
            }
        }
        if (messageId != null) {
            val pending =
                PendingTelegramConfirmation(message.challengeId, messageId, actionConfirmedText(message.language))
            bestEffort(userId, "remember the Telegram action confirmation") { pendingDataSource.save(userId, pending) }
        } else {
            logger.warn("Telegram sent the action confirmation of user {} without a message id", userId)
            if (previous != null) {
                bestEffort(userId, "forget the previous Telegram action confirmation") {
                    pendingDataSource.forget(userId, previous)
                }
            }
        }
    }

    private suspend fun chatOf(userId: Uuid): Long? {
        return transactionManager.transactionOrThrow { targetDataSource.getTelegram(userId)?.toLongOrNull() }
    }

    private suspend fun <T> bestEffort(userId: Uuid, step: String, block: suspend () -> T): T? {
        return runSuspendCatching { block() }
            .onFailure { logger.warn("Failed to {} of user {}", step, userId, it) }
            .getOrNull()
    }

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(TelegramConfirmationChat::class.java)
    }
}
