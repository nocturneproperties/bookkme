package com.bookk.auth.data.datasource

import com.bookk.auth.data.orm.table.DemoSettingsTable
import com.bookk.auth.domain.api.demo.entity.DemoAccountPolicy
import com.bookk.auth.domain.datasource.DemoSettingsDataSource
import com.bookk.core.data.DataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select

internal class DemoSettingsDataSourceImpl : DataSource(), DemoSettingsDataSource {

    override suspend fun getMaxActiveAccounts(): Int = dbQuery {
        val storedCap = DemoSettingsTable.select(DemoSettingsTable.maxActiveAccounts)
            .where { DemoSettingsTable.id eq DemoSettingsTable.ROW_ID }
            .singleOrNull()
            ?.get(DemoSettingsTable.maxActiveAccounts)
        storedCap ?: DemoAccountPolicy.DEFAULT_MAX_ACTIVE_ACCOUNTS
    }
}
