package com.bookk.notifications.data.di

import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.database.createDatabase
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.notifications.data.datasource.DeviceDataSourceImpl
import com.bookk.notifications.data.datasource.NotificationSettingsDataSourceImpl
import com.bookk.notifications.data.datasource.NotificationTargetDataSourceImpl
import com.bookk.notifications.domain.api.NOTIFICATIONS_SCHEMA
import com.bookk.notifications.domain.api.NOTIFICATIONS_SHORT_RETENTION_SCHEMA
import com.bookk.notifications.domain.datasource.DeviceDataSource
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import library.encryption.impl.di.subjectKeys
import library.outbox.di.exhaustedEventDataSource
import library.outbox.di.personalDataCipher
import library.outbox.personal.ClientKey
import org.koin.core.module.dsl.scopedOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

fun notificationsDataModule(environment: (String) -> String? = System::getenv) = module {
    scope(named(NOTIFICATIONS_SCHEMA)) {
        subjectKeys(NOTIFICATIONS_SHORT_RETENTION_SCHEMA, environment)
        personalDataCipher(ClientKey.LINKED_USER_ID)
        exhaustedEventDataSource()
        scoped { createDatabase(schemaName = NOTIFICATIONS_SCHEMA) }
        scoped<TransactionManager> { ExposedTransactionManager(get()) }
        scopedOf(::DeviceDataSourceImpl) bind DeviceDataSource::class
        scopedOf(::NotificationSettingsDataSourceImpl) bind NotificationSettingsDataSource::class
        scopedOf(::NotificationTargetDataSourceImpl) bind NotificationTargetDataSource::class
    }
}
