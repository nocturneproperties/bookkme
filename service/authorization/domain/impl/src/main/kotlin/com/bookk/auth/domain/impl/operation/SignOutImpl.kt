package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.signout.operation.SignOut
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class SignOutImpl(
    private val deviceDataSource: DeviceDataSource,
    private val transactionManager: TransactionManager
) : SignOut {

    override suspend fun invoke(deviceId: Uuid): Result<Unit> = transactionManager.transaction {
        deviceDataSource.deleteTokenFromDevice(deviceId)
    }
}