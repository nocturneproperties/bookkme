package com.bookk.notifications.domain.impl.channel

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.notifications.domain.datasource.DeviceDataSource
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode.UNREGISTERED
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

internal class FirebaseNotificationSender(
    private val transactionManager: TransactionManager,
    private val deviceDataSource: DeviceDataSource,
    private val firebaseMessaging: FirebaseMessaging,
) : NotificationSender {

    private val logger = LoggerFactory.getLogger(FirebaseNotificationSender::class.java)

    override suspend fun send(toUserId: Uuid, params: NotificationParameters) = transactionManager.transaction {
        val devices = deviceDataSource.getByUserId(toUserId)
        val devicesWithTokens = devices.filter { it.notificationToken != null }

        devicesWithTokens.forEach { device ->
            val token = requireNotNull(device.notificationToken)
            val content = params.push(device.language)
            val message = Message.builder()
                .setFid(token)
                .setNotification(
                    Notification.builder()
                        .setTitle(content.title)
                        .setBody(content.body)
                        .build()
                )
                .build()

            runCatching {
                firebaseMessaging.send(message)
            }.onFailure { error ->
                when {
                    error is FirebaseMessagingException && error.messagingErrorCode == UNREGISTERED -> {
                        deviceDataSource.updateToken(device.deviceUuid, null)
                    }
                    else -> logger.error("Failed to send message to device: ${device.deviceUuid}", error)
                }
            }
        }
    }
}
