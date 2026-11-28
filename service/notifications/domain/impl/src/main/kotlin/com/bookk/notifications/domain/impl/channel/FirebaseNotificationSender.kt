package com.bookk.notifications.domain.impl.channel

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.domain.entity.throwIfAnyFailed
import com.bookk.notifications.domain.api.entity.Device
import com.bookk.notifications.domain.datasource.DeviceDataSource
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.bookk.notifications.domain.impl.notification.PushNotification
import com.bookk.notifications.domain.impl.notification.PushSubject
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode.UNREGISTERED
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

private const val APNS_COLLAPSE_ID_HEADER = "apns-collapse-id"

internal class FirebaseNotificationSender(
    private val transactionManager: TransactionManager,
    private val deviceDataSource: DeviceDataSource,
    private val firebaseMessaging: FirebaseMessaging,
) : NotificationSender {

    private val logger = LoggerFactory.getLogger(FirebaseNotificationSender::class.java)

    override suspend fun send(toUserId: Uuid, params: NotificationParameters): Result<Unit> = runSuspendCatching {
        val devices = transactionManager.transactionOrThrow { deviceDataSource.getByUserId(toUserId) }
        devices
            .filter { it.notificationToken != null }
            .map { device -> runSuspendCatching { push(device, params) } }
            .throwIfAnyFailed()
    }

    private suspend fun push(device: Device, params: NotificationParameters) {
        val message = message(requireNotNull(device.notificationToken), params.push(device.language))

        runCatching { firebaseMessaging.send(message) }
            .onFailure { error ->
                if (error is FirebaseMessagingException && error.messagingErrorCode == UNREGISTERED) {
                    transactionManager.transactionOrThrow {
                        deviceDataSource.updateToken(device.authId, device.deviceUuid, null)
                    }
                } else {
                    logger.error("Failed to send message to device: ${device.deviceUuid}", error)
                }
            }
    }

    private fun message(token: String, content: PushNotification): Message {
        val builder = Message.builder()
            .setFid(token)
            .setNotification(
                Notification.builder()
                    .setTitle(content.title)
                    .setBody(content.body)
                    .build(),
            )
        content.subject?.let { builder.identifiedBy(it) }
        return builder.build()
    }

    private fun Message.Builder.identifiedBy(subject: PushSubject) {
        putAllData(subject.data)
        setAndroidConfig(
            AndroidConfig.builder()
                .setNotification(AndroidNotification.builder().setTag(subject.key).build())
                .build(),
        )
        setApnsConfig(
            ApnsConfig.builder()
                .putHeader(APNS_COLLAPSE_ID_HEADER, subject.key)
                .setAps(Aps.builder().build())
                .build(),
        )
    }
}
