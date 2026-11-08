package com.bookk.notifications.domain.impl.channel

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.notifications.domain.datasource.DeviceDataSource
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.bookk.notifications.domain.impl.notification.renderer.emailContent
import library.email.EmailMessage
import library.email.EmailSender
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

internal class EmailNotificationSender(
    private val transactionManager: TransactionManager,
    private val targetDataSource: NotificationTargetDataSource,
    private val deviceDataSource: DeviceDataSource,
    private val emailSender: EmailSender,
) : NotificationSender {

    private val logger = LoggerFactory.getLogger(EmailNotificationSender::class.java)

    override suspend fun send(toUserId: Uuid, params: NotificationParameters): Result<Unit> = runSuspendCatching {
        val recipient = transactionManager.transactionOrThrow { findRecipient(toUserId) } ?: return@runSuspendCatching
        val message = EmailMessage(to = recipient.address, content = params.emailContent(recipient.language))
        emailSender.send(message)
            .onFailure { logger.warn("Failed to email a {} notification to user {}", params.type, toUserId, it) }
            .getOrThrow()
    }

    private suspend fun findRecipient(userId: Uuid): Recipient? {
        val address = targetDataSource.getEmail(userId) ?: return null
        val language = deviceDataSource.getLastUsedLanguage(userId) ?: Language.EN
        return Recipient(address, language)
    }

    private class Recipient(val address: String, val language: Language)
}
