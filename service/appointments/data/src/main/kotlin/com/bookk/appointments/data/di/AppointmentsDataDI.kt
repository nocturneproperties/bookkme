package com.bookk.appointments.data.di

import com.bookk.appointments.data.datasource.AppointmentDataSourceImpl
import com.bookk.appointments.data.datasource.AppointmentPermissionDataSourceImpl
import com.bookk.appointments.data.datasource.AppointmentRequestDataSourceImpl
import com.bookk.appointments.data.datasource.AppointmentSettingsDataSourceImpl
import com.bookk.appointments.data.datasource.AppointmentSubscriptionDataSourceImpl
import com.bookk.appointments.data.datasource.DeletedClientDataSourceImpl
import com.bookk.appointments.data.datasource.DeletedUserDataSourceImpl
import com.bookk.appointments.data.datasource.UserDataExportDataSourceImpl
import com.bookk.appointments.domain.api.APPOINTMENTS_SCHEMA
import com.bookk.appointments.domain.api.APPOINTMENTS_SHORT_RETENTION_SCHEMA
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.appointments.domain.datasource.DeletedClientDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.appointments.domain.datasource.UserDataExportDataSource
import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.database.createDatabase
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.encryption.impl.di.subjectKeys
import library.outbox.di.exhaustedEventDataSource
import library.outbox.di.personalDataCipher
import org.koin.core.module.dsl.scopedOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

fun appointmentsDataModule(environment: (String) -> String? = System::getenv) = module {
    scope(named(APPOINTMENTS_SCHEMA)) {
        subjectKeys(APPOINTMENTS_SHORT_RETENTION_SCHEMA, environment)
        personalDataCipher()
        exhaustedEventDataSource()
        scopedOf(::AppointmentDataSourceImpl) bind AppointmentDataSource::class
        scopedOf(::UserDataExportDataSourceImpl) bind UserDataExportDataSource::class
        scopedOf(::AppointmentRequestDataSourceImpl) bind AppointmentRequestDataSource::class
        scopedOf(::AppointmentSettingsDataSourceImpl) bind AppointmentSettingsDataSource::class
        scopedOf(::AppointmentSubscriptionDataSourceImpl) bind AppointmentSubscriptionDataSource::class
        scopedOf(::AppointmentPermissionDataSourceImpl) bind AppointmentPermissionDataSource::class
        scopedOf(::DeletedUserDataSourceImpl) bind DeletedUserDataSource::class
        scopedOf(::DeletedClientDataSourceImpl) bind DeletedClientDataSource::class
        scoped { createDatabase(schemaName = APPOINTMENTS_SCHEMA) }
        scoped<TransactionManager> { ExposedTransactionManager(get()) }
    }
}
