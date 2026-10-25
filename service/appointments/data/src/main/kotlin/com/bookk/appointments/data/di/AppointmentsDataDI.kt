package com.bookk.appointments.data.di

import com.bookk.appointments.data.datasource.AppointmentDataSourceImpl
import com.bookk.appointments.data.datasource.AppointmentPermissionDataSourceImpl
import com.bookk.appointments.data.datasource.AppointmentRequestDataSourceImpl
import com.bookk.appointments.data.datasource.AppointmentSettingsDataSourceImpl
import com.bookk.appointments.data.datasource.AppointmentSubscriptionDataSourceImpl
import com.bookk.appointments.domain.api.APPOINTMENTS_SCHEMA
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.database.createDatabase
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.data.datasource.ExhaustedEventDataSourceImpl
import com.bookk.core.domain.datasource.transaction.TransactionManager
import org.koin.core.module.dsl.scopedOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

fun appointmentsDataModule() = module {
    scope(named(APPOINTMENTS_SCHEMA)) {
        scopedOf(::AppointmentDataSourceImpl) bind AppointmentDataSource::class
        scopedOf(::AppointmentRequestDataSourceImpl) bind AppointmentRequestDataSource::class
        scopedOf(::AppointmentSettingsDataSourceImpl) bind AppointmentSettingsDataSource::class
        scopedOf(::AppointmentSubscriptionDataSourceImpl) bind AppointmentSubscriptionDataSource::class
        scopedOf(::AppointmentPermissionDataSourceImpl) bind AppointmentPermissionDataSource::class
        scopedOf(::ExhaustedEventDataSourceImpl) bind ExhaustedEventDataSource::class
        scoped { createDatabase(schemaName = APPOINTMENTS_SCHEMA) }
        scoped<TransactionManager> { ExposedTransactionManager(get()) }
    }
}
