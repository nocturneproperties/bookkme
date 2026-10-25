package com.bookk.business.data.di

import com.bookk.business.data.datasource.BusinessDataSourceImpl
import com.bookk.business.data.datasource.BusinessPermissionDataSourceImpl
import com.bookk.business.data.datasource.ClientDataSourceImpl
import com.bookk.business.data.datasource.EmployeeDataSourceImpl
import com.bookk.business.data.datasource.EmployeeInvitationDataSourceImpl
import com.bookk.business.data.datasource.ServiceDataSourceImpl
import com.bookk.business.domain.api.BUSINESS_SCHEMA
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.business.domain.datasource.EmployeeInvitationDataSource
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.database.createDatabase
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.data.datasource.ExhaustedEventDataSourceImpl
import com.bookk.core.domain.datasource.transaction.TransactionManager
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun businessDataModule() = module {
    scope(named(BUSINESS_SCHEMA)) {
        scoped<BusinessDataSource> { BusinessDataSourceImpl() }
        scoped<BusinessPermissionDataSource> { BusinessPermissionDataSourceImpl() }
        scoped<ClientDataSource> { ClientDataSourceImpl() }
        scoped<ServiceDataSource> { ServiceDataSourceImpl() }
        scoped<EmployeeDataSource> { EmployeeDataSourceImpl() }
        scoped<EmployeeInvitationDataSource> { EmployeeInvitationDataSourceImpl() }
        scoped<ExhaustedEventDataSource> { ExhaustedEventDataSourceImpl() }
        scoped { createDatabase(schemaName = BUSINESS_SCHEMA) }
        scoped<TransactionManager> { ExposedTransactionManager(get()) }
    }
}
