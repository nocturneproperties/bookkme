package com.bookk.notifications.data.di

import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.database.createDatabase
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.data.datasource.ExhaustedEventDataSourceImpl
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.notifications.data.datasource.DeviceDataSourceImpl
import com.bookk.notifications.data.datasource.NotificationSettingsDataSourceImpl
import com.bookk.notifications.data.datasource.NotificationTargetDataSourceImpl
import com.bookk.notifications.domain.api.NOTIFICATIONS_SCHEMA
import com.bookk.notifications.domain.datasource.DeviceDataSource
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import org.koin.core.module.dsl.scopedOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

fun notificationsDataModule() = module {
    scope(named(NOTIFICATIONS_SCHEMA)) {
        scoped { createDatabase(schemaName = NOTIFICATIONS_SCHEMA) }
        scoped<TransactionManager> { ExposedTransactionManager(get()) }
        scopedOf(::DeviceDataSourceImpl) bind DeviceDataSource::class
        scopedOf(::NotificationSettingsDataSourceImpl) bind NotificationSettingsDataSource::class
        scopedOf(::NotificationTargetDataSourceImpl) bind NotificationTargetDataSource::class
        scopedOf(::ExhaustedEventDataSourceImpl) bind ExhaustedEventDataSource::class
    }
}
