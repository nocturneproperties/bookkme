package com.bookk.user.data.di

import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.database.createDatabase
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.user.data.datasource.LegalDocumentDataSourceImpl
import com.bookk.user.data.datasource.UserDataExportDataSourceImpl
import com.bookk.user.data.datasource.UserDataSourceImpl
import com.bookk.user.domain.api.USER_SCHEMA
import com.bookk.user.domain.datasource.LegalDocumentDataSource
import com.bookk.user.domain.datasource.UserDataExportDataSource
import com.bookk.user.domain.datasource.UserDataSource
import library.export.impl.di.dataExportModule
import library.outbox.datasource.ExhaustedEventDataSourceImpl
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun userDataModule() = module {
    includes(dataExportModule(named(USER_SCHEMA)))
    scope(named(USER_SCHEMA)) {
        scoped<UserDataSource> { UserDataSourceImpl(get()) }
        scoped<LegalDocumentDataSource> { LegalDocumentDataSourceImpl() }
        scoped<UserDataExportDataSource> { UserDataExportDataSourceImpl() }
        scoped<ExhaustedEventDataSource> { ExhaustedEventDataSourceImpl() }
        scoped { createDatabase(schemaName = USER_SCHEMA) }
        scoped<TransactionManager> { ExposedTransactionManager(get()) }
    }
}
