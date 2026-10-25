package com.bookk.auth.domain.impl.operation.identification

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.api.identification.operation.GetAttachPasskeyToAccountChallenge
import com.bookk.auth.domain.api.identification.operation.GetAttachPasskeyToAccountChallenge.Error.UnableToGeneratePasskeyChallenge
import com.bookk.auth.domain.api.registration.entity.RegistrationChallengeResponse
import com.bookk.auth.domain.api.registration.operation.StartPasskeyRegistration
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.user.client.UserClient
import kotlin.uuid.Uuid

internal class GetAttachPasskeyToAccountChallengeImpl(
    private val startPasskeyRegistration: StartPasskeyRegistration,
    private val accountDataSource: AccountDataSource,
    private val deviceDataSource: DeviceDataSource,
    private val userClient: UserClient,
    private val transactionManager: TransactionManager
) : GetAttachPasskeyToAccountChallenge {
    override suspend fun invoke(authId: Uuid, deviceId: Uuid, userId: Uuid): Result<RegistrationChallengeResponse> {
        return runSuspendCatching {
            val auth = getAuthorizationInfo(authId)
            val device = getDeviceInfo(deviceId)
            val user = userClient.getUserById(userId).getOrElse { throw UnableToGeneratePasskeyChallenge() }
            val handle = auth.uuid
            val displayName = "${user.name} ${user.lastName} - ${device.deviceInfo.deviceName}"
            return startPasskeyRegistration(handle, displayName)
        }
    }

    private suspend fun getAuthorizationInfo(authId: Uuid): Authentication {
        return transactionManager.transaction {
            accountDataSource.getAuthRecordById(authId) ?: throw UnableToGeneratePasskeyChallenge()
        }.getOrThrow()
    }

    private suspend fun getDeviceInfo(deviceId: Uuid): Device {
        return transactionManager.transaction {
            deviceDataSource.getDeviceById(deviceId) ?: throw UnableToGeneratePasskeyChallenge()
        }.getOrThrow()
    }
}