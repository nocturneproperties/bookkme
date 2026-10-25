package com.bookk.auth.domain.impl.operation.token

import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Error.InvalidCredentials
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Source
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.core.AppLevelConstants
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.server.auth.client.AuthClaim
import library.signing.TokenIssuer
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class GenerateAuthTokenImpl(
    private val deviceDataSource: DeviceDataSource,
    private val transactionManager: TransactionManager,
    private val tokenIssuer: TokenIssuer
) : GenerateAuthToken {

    override suspend fun invoke(source: Source): Result<AuthTokens> = transactionManager.transaction {
        val deviceRecord = source.getDevice() ?: throw InvalidCredentials()
        val accessToken = createAccessToken(deviceRecord)
        val refreshToken = OpaqueRefreshToken.generate()
        when (source) {
            is Source.RefreshToken -> deviceDataSource.rotateRefreshToken(
                deviceRecord.deviceInfo.id,
                refreshToken.id,
                refreshToken.secretHash
            )

            is Source.InitialAuthentication -> deviceDataSource.attachRefreshTokenToDevice(
                deviceRecord.deviceInfo.id,
                refreshToken.id,
                refreshToken.secretHash
            )
        }
        AuthTokens(accessToken, refreshToken.token)
    }

    private suspend fun createAccessToken(record: Device): String {
        return tokenIssuer.issue(ACCESS_TTL) {
            withJWTId(Uuid.random().toString())
                .withAudience(AppLevelConstants.domainName)
                .withClaim(AuthClaim.AUTH_ID.key, record.authRecord.id.toString())
                .withClaim(AuthClaim.USER_ID.key, record.authRecord.userId.toString())
                .withClaim(AuthClaim.DEVICE_ID.key, record.deviceInfo.id.toString())
        }
    }

    private suspend fun Source.getDevice(): Device? {
        return when (this) {
            is Source.RefreshToken -> validateRefreshToken(token)
            is Source.InitialAuthentication -> deviceDataSource.getDeviceByAuthIdAndUUID(authId, deviceUUID)
        }
    }

    private suspend fun validateRefreshToken(token: String): Device? {
        val refreshToken = OpaqueRefreshToken.parse(token) ?: return null
        val device = deviceDataSource.getDeviceByRefreshTokenId(refreshToken.id) ?: return null
        val currentDeviceToken = device.deviceInfo.refreshToken ?: return null
        val previousDeviceToken = device.deviceInfo.previousRefreshToken

        if (previousDeviceToken != null && OpaqueRefreshToken.matches(refreshToken, previousDeviceToken)) {
            deviceDataSource.deleteTokenFromDevice(device.deviceInfo.id)
            return null
        }
        if (OpaqueRefreshToken.matches(refreshToken, currentDeviceToken)) {
            return device
        }
        return null
    }

    companion object {
        private val ACCESS_TTL = 5.minutes
    }
}