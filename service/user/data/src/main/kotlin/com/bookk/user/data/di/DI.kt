package com.bookk.user.data.di

import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.database.createDatabase
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.user.data.datasource.LegalDocumentDataSourceImpl
import com.bookk.user.data.datasource.UserDataExportDataSourceImpl
import com.bookk.user.data.datasource.UserDataSourceImpl
import com.bookk.user.domain.api.USER_SCHEMA
import com.bookk.user.domain.api.USER_SHORT_RETENTION_SCHEMA
import com.bookk.user.domain.datasource.LegalDocumentDataSource
import com.bookk.user.domain.datasource.UserDataExportDataSource
import com.bookk.user.domain.datasource.UserDataSource
import library.encryption.impl.di.subjectKeys
import library.export.impl.di.dataExportModule
import library.outbox.di.exhaustedEventDataSource
import library.outbox.di.personalDataCipher
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun userDataModule(environment: (String) -> String? = System::getenv) = module {
    includes(dataExportModule(named(USER_SCHEMA)))
    scope(named(USER_SCHEMA)) {
        subjectKeys(USER_SHORT_RETENTION_SCHEMA, environment)
        personalDataCipher()
        exhaustedEventDataSource()
        scoped<UserDataSource> { UserDataSourceImpl(get()) }
        scoped<LegalDocumentDataSource> { LegalDocumentDataSourceImpl() }
        scoped<UserDataExportDataSource> { UserDataExportDataSourceImpl() }
        scoped { createDatabase(schemaName = USER_SCHEMA) }
        scoped<TransactionManager> { ExposedTransactionManager(get()) }
    }
}
