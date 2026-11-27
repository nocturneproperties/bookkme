package com.bookk.auth.domain.datasource

interface DemoSettingsDataSource {
    suspend fun getMaxActiveAccounts(): Int
}
