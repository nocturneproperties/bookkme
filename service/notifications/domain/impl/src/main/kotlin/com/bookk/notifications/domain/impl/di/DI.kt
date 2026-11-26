package com.bookk.notifications.domain.impl.di

import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.notifications.client.api.export.ExportUserNotificationData
import com.bookk.notifications.client.api.telegram.SendTelegramActionConfirmation
import com.bookk.notifications.domain.api.CreateDeviceEntry
import com.bookk.notifications.domain.api.DeleteDeviceByUUID
import com.bookk.notifications.domain.api.DeleteUserNotificationData
import com.bookk.notifications.domain.api.GetNotificationSettings
import com.bookk.notifications.domain.api.NOTIFICATIONS_SCHEMA
import com.bookk.notifications.domain.api.UpdateNotificationSettings
import com.bookk.notifications.domain.api.UpdatePushNotificationToken
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.impl.ClearPushNotificationToken
import com.bookk.notifications.domain.impl.CreateDeviceEntryImpl
import com.bookk.notifications.domain.impl.DeleteDeviceByUUIDImpl
import com.bookk.notifications.domain.impl.DeleteUserNotificationDataImpl
import com.bookk.notifications.domain.impl.ExportUserNotificationDataImpl
import com.bookk.notifications.domain.impl.GetNotificationSettingsImpl
import com.bookk.notifications.domain.impl.UpdateDeviceLanguage
import com.bookk.notifications.domain.impl.UpdateNotificationSettingsImpl
import com.bookk.notifications.domain.impl.UpdatePushNotificationTokenImpl
import com.bookk.notifications.domain.impl.UpdateTargetInformation
import com.bookk.notifications.domain.impl.channel.EmailNotificationSender
import com.bookk.notifications.domain.impl.channel.FirebaseNotificationSender
import com.bookk.notifications.domain.impl.channel.NotificationSender
import com.bookk.notifications.domain.impl.channel.TelegramNotificationSender
import com.bookk.notifications.domain.impl.channel.telegram.TelegramMessenger
import com.bookk.notifications.domain.impl.channel.telegram.TelegramMiniAppLink
import com.bookk.notifications.domain.impl.channel.telegram.telegramMessenger
import com.bookk.notifications.domain.impl.channel.telegram.telegramMiniAppLink
import com.bookk.notifications.domain.impl.event.NotificationEventHandler
import com.bookk.notifications.domain.impl.notification.SendNotification
import com.bookk.notifications.domain.impl.telegram.SendTelegramActionConfirmationImpl
import com.google.firebase.messaging.FirebaseMessaging
import io.ktor.client.engine.cio.CIO
import org.koin.core.module.Module
import org.koin.core.module.dsl.scopedOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val NotificationsScope: Qualifier = named(NOTIFICATIONS_SCHEMA)

fun notificationsDomainModule(environment: (String) -> String? = System::getenv): Module {
    val miniAppLink = telegramMiniAppLink(environment)
    return notificationsDomainModule(environment, miniAppLink)
}

private fun notificationsDomainModule(
    environment: (String) -> String?,
    miniAppLink: TelegramMiniAppLink?,
): Module = module {
    scope(NotificationsScope) {
        scopedOf(::NotificationEventHandler) bind EventHandler::class
        scopedOf(::CreateDeviceEntryImpl) bind CreateDeviceEntry::class
        scopedOf(::DeleteDeviceByUUIDImpl) bind DeleteDeviceByUUID::class
        scopedOf(::DeleteUserNotificationDataImpl) bind DeleteUserNotificationData::class
        scopedOf(::UpdatePushNotificationTokenImpl) bind UpdatePushNotificationToken::class
        scopedOf(::UpdateNotificationSettingsImpl) bind UpdateNotificationSettings::class
        scopedOf(::GetNotificationSettingsImpl) bind GetNotificationSettings::class
        scopedOf(::ExportUserNotificationDataImpl) bind ExportUserNotificationData::class
        scopedOf(::FirebaseNotificationSender) bind NotificationSender::class
        scopedOf(::EmailNotificationSender) bind NotificationSender::class
        scopedOf(::TelegramNotificationSender) bind NotificationSender::class
        scopedOf(::SendNotification)
        scopedOf(::UpdateTargetInformation)
        scopedOf(::UpdateDeviceLanguage)
        scopedOf(::ClearPushNotificationToken)
        scoped<Map<CommunicationChannel, NotificationSender>> {
            mapOf(
                CommunicationChannel.EMAIL to get<EmailNotificationSender>(),
                CommunicationChannel.TELEGRAM to get<TelegramNotificationSender>(),
                CommunicationChannel.PUSH_NOTIFICATIONS to get<FirebaseNotificationSender>(),
            )
        }
        scoped { FirebaseMessaging.getInstance() }
        scoped<TelegramMessenger> { telegramMessenger(environment, CIO.create()) }
        scoped<SendTelegramActionConfirmation> {
            SendTelegramActionConfirmationImpl(get(), get(), get(), miniAppLink)
        }
    }
}
