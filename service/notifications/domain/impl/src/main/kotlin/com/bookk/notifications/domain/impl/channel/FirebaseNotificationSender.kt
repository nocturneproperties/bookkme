package com.bookk.notifications.domain.impl.channel

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.domain.entity.throwIfAnyFailed
import com.bookk.notifications.domain.api.entity.Device
import com.bookk.notifications.domain.datasource.DeviceDataSource
import com.bookk.notifications.domain.impl.notification.DataPush
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
private const val APNS_PUSH_TYPE_HEADER = "apns-push-type"
private const val APNS_PRIORITY_HEADER = "apns-priority"
private const val APNS_BACKGROUND_PUSH_TYPE = "background"
private const val APNS_POWER_CONSIDERATE_PRIORITY = "5"

internal class FirebaseNotificationSender(
    private val transactionManager: TransactionManager,
    private val deviceDataSource: DeviceDataSource,
    private val firebaseMessaging: FirebaseMessaging,
) : NotificationSender, DataPushSender {

    private val logger = LoggerFactory.getLogger(FirebaseNotificationSender::class.java)

    override suspend fun send(toUserId: Uuid, params: NotificationParameters): Result<Unit> {
        return pushToDevices(toUserId) { device, token -> message(token, params.push(device.language)) }
    }

    override suspend fun sendData(toUserId: Uuid, push: DataPush): Result<Unit> {
        return pushToDevices(toUserId) { _, token -> dataMessage(token, push) }
    }

    private suspend fun pushToDevices(
        toUserId: Uuid,
        message: (Device, String) -> Message,
    ): Result<Unit> = runSuspendCatching {
        val devices = transactionManager.transactionOrThrow { deviceDataSource.getByUserId(toUserId) }
        devices
            .mapNotNull { device -> device.notificationToken?.let { token -> device to token } }
            .map { (device, token) -> runSuspendCatching { push(device, message(device, token)) } }
            .throwIfAnyFailed()
    }

    private suspend fun push(device: Device, message: Message) {
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

    private fun dataMessage(token: String, push: DataPush): Message {
        return Message.builder()
            .setFid(token)
            .putAllData(push.data)
            .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.NORMAL).build())
            .setApnsConfig(
                ApnsConfig.builder()
                    .putHeader(APNS_PUSH_TYPE_HEADER, APNS_BACKGROUND_PUSH_TYPE)
                    .putHeader(APNS_PRIORITY_HEADER, APNS_POWER_CONSIDERATE_PRIORITY)
                    .setAps(Aps.builder().setContentAvailable(true).build())
                    .build(),
            )
            .build()
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
