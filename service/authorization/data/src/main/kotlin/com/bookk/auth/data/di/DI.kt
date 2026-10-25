package com.bookk.auth.data.di

import com.bookk.auth.data.datasource.AccountDataSourceImpl
import com.bookk.auth.data.datasource.DeviceDataSourceImpl
import com.bookk.auth.data.datasource.PassKeyDataSourceImpl
import com.bookk.auth.data.datasource.YubicoCredentialRepository
import com.bookk.auth.domain.api.AUTH_SCHEMA
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.auth.domain.repository.CacheableCredentialRepository
import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.database.createDatabase
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.data.datasource.ExhaustedEventDataSourceImpl
import com.bookk.core.domain.datasource.transaction.TransactionManager
import org.koin.core.module.dsl.scopedOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

private val AuthScope: Qualifier = named(AUTH_SCHEMA)

fun authDataModule() = module {
    scope(AuthScope) {
        scopedOf(::AccountDataSourceImpl) bind AccountDataSource::class
        scopedOf(::DeviceDataSourceImpl) bind DeviceDataSource::class
        scopedOf(::PassKeyDataSourceImpl) bind PassKeyDataSource::class
        scopedOf(::YubicoCredentialRepository) bind CacheableCredentialRepository::class
        scopedOf(::ExhaustedEventDataSourceImpl) bind ExhaustedEventDataSource::class
        scoped { createDatabase(schemaName = AUTH_SCHEMA) }
        scoped<TransactionManager> { ExposedTransactionManager(get()) }
    }
}