package com.bookk.notifications.domain.impl.channel

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.datasource.DeviceDataSource
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import com.bookk.notifications.domain.impl.channel.telegram.TelegramDelivery
import com.bookk.notifications.domain.impl.channel.telegram.TelegramMessenger
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

internal class TelegramNotificationSender(
    private val transactionManager: TransactionManager,
    private val targetDataSource: NotificationTargetDataSource,
    private val deviceDataSource: DeviceDataSource,
    private val notificationSettingsDataSource: NotificationSettingsDataSource,
    private val telegramMessenger: TelegramMessenger,
) : NotificationSender {

    private val logger = LoggerFactory.getLogger(TelegramNotificationSender::class.java)

    override suspend fun send(toUserId: Uuid, params: NotificationParameters): Result<Unit> {
        return runSuspendCatching { deliver(toUserId, params) }
            .onFailure {
                logger.warn("Failed to send a {} notification to user {} on Telegram", params.type, toUserId, it)
            }
    }

    private suspend fun deliver(userId: Uuid, params: NotificationParameters) {
        val recipient = transactionManager.transactionOrThrow { findRecipient(userId) } ?: return
        val delivery = telegramMessenger.send(recipient.chatId, params.text(recipient.language).text)
        if (delivery != TelegramDelivery.ChatUnreachable) return
        logger.info("User {} blocked the bot or never allowed it to write, so Telegram is turned off for them", userId)
        transactionManager.transactionOrThrow { turnTelegramOff(userId) }
    }

    private suspend fun findRecipient(userId: Uuid): Recipient? {
        val chatId = targetDataSource.getTelegram(userId)?.toLongOrNull() ?: return null
        val language = deviceDataSource.getLastUsedLanguage(userId) ?: Language.EN
        return Recipient(chatId, language)
    }

    private suspend fun turnTelegramOff(userId: Uuid) {
        val settings = notificationSettingsDataSource.getByUserId(userId) ?: return
        val telegram = settings.channels.firstOrNull { it.channel == CommunicationChannel.TELEGRAM } ?: return
        if (!telegram.enabled) return
        notificationSettingsDataSource.upsert(telegram.copy(enabled = false))
    }

    private class Recipient(val chatId: Long, val language: Language)
}
