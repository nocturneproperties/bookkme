package com.bookk.notifications.domain.impl.di

import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.notifications.domain.api.CreateDeviceEntry
import com.bookk.notifications.domain.api.DeleteDeviceByUUID
import com.bookk.notifications.domain.api.DeleteUserNotificationData
import com.bookk.notifications.domain.api.GetNotificationSettings
import com.bookk.notifications.domain.api.NOTIFICATIONS_SCHEMA
import com.bookk.notifications.domain.api.UpdateNotificationSettings
import com.bookk.notifications.domain.api.UpdatePushNotificationToken
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.impl.CreateDeviceEntryImpl
import com.bookk.notifications.domain.impl.DeleteDeviceByUUIDImpl
import com.bookk.notifications.domain.impl.DeleteUserNotificationDataImpl
import com.bookk.notifications.domain.impl.GetNotificationSettingsImpl
import com.bookk.notifications.domain.impl.UpdateDeviceLanguage
import com.bookk.notifications.domain.impl.UpdateNotificationSettingsImpl
import com.bookk.notifications.domain.impl.UpdatePushNotificationTokenImpl
import com.bookk.notifications.domain.impl.UpdateTargetInformation
import com.bookk.notifications.domain.impl.channel.EmailNotificationSender
import com.bookk.notifications.domain.impl.channel.FirebaseNotificationSender
import com.bookk.notifications.domain.impl.channel.NotificationSender
import com.bookk.notifications.domain.impl.channel.TelegramNotificationSender
import com.bookk.notifications.domain.impl.event.NotificationEventHandler
import com.bookk.notifications.domain.impl.notification.SendNotification
import com.google.firebase.messaging.FirebaseMessaging
import org.koin.core.module.dsl.scopedOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val NotificationsScope: Qualifier = named(NOTIFICATIONS_SCHEMA)

fun notificationsDomainModule() = module {
    scope(NotificationsScope) {
        scopedOf(::NotificationEventHandler) bind EventHandler::class
        scopedOf(::CreateDeviceEntryImpl) bind CreateDeviceEntry::class
        scopedOf(::DeleteDeviceByUUIDImpl) bind DeleteDeviceByUUID::class
        scopedOf(::DeleteUserNotificationDataImpl) bind DeleteUserNotificationData::class
        scopedOf(::UpdatePushNotificationTokenImpl) bind UpdatePushNotificationToken::class
        scopedOf(::UpdateNotificationSettingsImpl) bind UpdateNotificationSettings::class
        scopedOf(::GetNotificationSettingsImpl) bind GetNotificationSettings::class
        scopedOf(::FirebaseNotificationSender) bind NotificationSender::class
        scopedOf(::EmailNotificationSender) bind NotificationSender::class
        scopedOf(::TelegramNotificationSender) bind NotificationSender::class
        scopedOf(::SendNotification)
        scopedOf(::UpdateTargetInformation)
        scopedOf(::UpdateDeviceLanguage)
        scoped<Map<CommunicationChannel, NotificationSender>> {
            mapOf(
                CommunicationChannel.EMAIL to get<EmailNotificationSender>(),
                CommunicationChannel.TELEGRAM to get<TelegramNotificationSender>(),
                CommunicationChannel.PUSH_NOTIFICATIONS to get<FirebaseNotificationSender>()
            )
        }
        scoped { FirebaseMessaging.getInstance() }
    }
}
