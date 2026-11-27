package com.bookk.auth.domain.impl.operation.demo

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.demo.entity.DemoAccountPolicy
import com.bookk.auth.domain.api.demo.entity.DemoCaller
import com.bookk.auth.domain.api.demo.operation.DemoUpgradeError.NotDemoAccount
import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Error.DemoExpired
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Error.InvalidCredentials
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.auth.domain.impl.operation.token.OpaqueRefreshToken
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class DemoCallerResolver(
    private val deviceDataSource: DeviceDataSource,
    private val transactionManager: TransactionManager,
) {
    suspend fun resolve(caller: DemoCaller): DemoSession {
        val device = transactionManager.transactionOrThrow {
            when (caller) {
                is DemoCaller.AccessToken -> deviceOfAccessToken(caller.authId, caller.deviceId)
                is DemoCaller.RefreshToken -> deviceOfRefreshToken(caller.token)
            }
        }
        val account = device.authRecord
        val demoEnd = account.demoExpiresAt ?: throw NotDemoAccount()
        if (Clock.System.now() >= DemoAccountPolicy.upgradeDeadline(demoEnd)) throw DemoExpired()
        return DemoSession(account, device.deviceInfo.deviceUUID)
    }

    private suspend fun deviceOfAccessToken(authId: Uuid, deviceId: Uuid): Device {
        val device = deviceDataSource.getDeviceById(deviceId) ?: throw InvalidCredentials()
        if (device.authRecord.id != authId) throw InvalidCredentials()
        return device
    }

    private suspend fun deviceOfRefreshToken(token: String): Device {
        val refreshToken = OpaqueRefreshToken.parse(token) ?: throw InvalidCredentials()
        val device = deviceDataSource.getDeviceByRefreshTokenIdForUpdate(refreshToken.id) ?: throw InvalidCredentials()
        val current = device.deviceInfo.refreshToken ?: throw InvalidCredentials()
        if (!OpaqueRefreshToken.matches(refreshToken, current)) throw InvalidCredentials()
        return device
    }
}

internal class DemoSession(val account: Authentication, val deviceUuid: Uuid)
