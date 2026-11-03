package com.bookk.auth.domain.impl.operation.export

import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import com.bookk.server.auth.client.export.AccountDataExport
import com.bookk.server.auth.client.export.ExportAccountData
import kotlin.uuid.Uuid

internal class ExportAccountDataImpl(
    private val accountDataSource: AccountDataSource,
    private val passKeyDataSource: PassKeyDataSource,
    private val deviceDataSource: DeviceDataSource,
    private val transactionManager: TransactionManager,
) : ExportAccountData {

    override suspend fun invoke(userId: Uuid): Result<AccountDataExport> = transactionManager.transaction {
        val account = accountDataSource.getAccountActivity(userId) ?: throw Error.NotFound()
        AccountDataExport(
            createdAt = account.createdAt,
            lastActiveAt = account.lastActiveAt,
            passkeys = passKeyDataSource.getCredentialBy(account.authId).map {
                AccountDataExport.PasskeyExport(name = it.name, createdAt = it.createdAt, lastUsedAt = it.lastUsedAt, backedUp = it.isBackedUp)
            },
            devices = deviceDataSource.getDevices(account.authId).map {
                AccountDataExport.DeviceExport(name = it.deviceInfo.deviceName, signedIn = it.deviceInfo.isSignedIn, lastSignInAt = it.deviceInfo.lastLogInAt)
            },
        )
    }
}
