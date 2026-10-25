package com.bookk.user.data.di

import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.database.createDatabase
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.data.datasource.ExhaustedEventDataSourceImpl
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.user.data.datasource.CommunicationDataSourceImpl
import com.bookk.user.data.datasource.UserDataSourceImpl
import com.bookk.user.domain.api.USER_SCHEMA
import com.bookk.user.domain.datasource.CommunicationDataSource
import com.bookk.user.domain.datasource.UserDataSource
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun userDataModule() = module {
    scope(named(USER_SCHEMA)) {
        scoped<UserDataSource> { UserDataSourceImpl(get()) }
        scoped<CommunicationDataSource> { CommunicationDataSourceImpl() }
        scoped<ExhaustedEventDataSource> { ExhaustedEventDataSourceImpl() }
        scoped { createDatabase(schemaName = USER_SCHEMA) }
        scoped<TransactionManager> { ExposedTransactionManager(get()) }
    }
}
