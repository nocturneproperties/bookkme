package com.bookk.notifications.domain.impl.telegram

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.notifications.client.api.telegram.SendTelegramActionConfirmation
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationDelivery
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationDelivery.Outcome
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationMessage
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import com.bookk.notifications.domain.impl.channel.telegram.TelegramDelivery
import com.bookk.notifications.domain.impl.channel.telegram.TelegramMessenger
import com.bookk.notifications.domain.impl.channel.telegram.TelegramMiniAppLink
import com.bookk.notifications.domain.impl.channel.telegram.TelegramUrlButton
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

internal class SendTelegramActionConfirmationImpl(
    private val transactionManager: TransactionManager,
    private val targetDataSource: NotificationTargetDataSource,
    private val telegramMessenger: TelegramMessenger,
    private val miniAppLink: TelegramMiniAppLink?,
) : SendTelegramActionConfirmation {

    override suspend fun invoke(
        userId: Uuid,
        message: TelegramActionConfirmationMessage,
    ): Result<TelegramActionConfirmationDelivery> = runSuspendCatching {
        TelegramActionConfirmationDelivery(deliver(userId, message))
    }

    private suspend fun deliver(userId: Uuid, message: TelegramActionConfirmationMessage): Outcome {
        val link = miniAppLink ?: return Outcome.UNAVAILABLE
        val chatId = transactionManager.transactionOrThrow {
            targetDataSource.getTelegram(userId)?.toLongOrNull()
        } ?: return Outcome.NO_CHAT
        val button = TelegramUrlButton(
            text = actionConfirmationButton(message.language),
            url = link.launching(message.startParameter),
        )
        val text = actionConfirmationText(message.action, message.language)
        val delivery = runSuspendCatching { telegramMessenger.send(chatId, text, button) }
            .onFailure { logger.warn("Failed to send a Telegram action confirmation to user {}", userId, it) }
            .getOrNull()
        return when (delivery) {
            TelegramDelivery.SENT -> Outcome.SENT
            TelegramDelivery.CHAT_UNREACHABLE -> Outcome.CHAT_UNREACHABLE
            TelegramDelivery.DROPPED -> Outcome.UNAVAILABLE
            null -> Outcome.FAILED
        }
    }

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(SendTelegramActionConfirmationImpl::class.java)
    }
}
