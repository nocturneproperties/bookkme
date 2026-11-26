package com.bookk.auth.domain.impl.operation.token

import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Error.InvalidCredentials
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Source
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.auth.domain.impl.operation.DeviceSignOut
import com.bookk.core.AppLevelConstants
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.server.auth.client.AuthClaim
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import library.signing.TokenIssuer
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class GenerateAuthTokenImpl(
    private val deviceDataSource: DeviceDataSource,
    private val deviceSignOut: DeviceSignOut,
    private val transactionManager: TransactionManager,
    private val tokenIssuer: TokenIssuer,
    private val accountDataSource: AccountDataSource,
    meterRegistry: MeterRegistry,
) : GenerateAuthToken {

    private val reusedRefreshTokens = Counter.builder(REFRESH_TOKEN_REUSED_METRIC)
        .description("Rotated-out refresh tokens presented again, each signing its device out")
        .register(meterRegistry)

    override suspend fun invoke(source: Source): Result<AuthTokens> {
        val issued = transactionManager.transaction { issue(source) }
        return issued.mapCatching { outcome ->
            when (outcome) {
                is Issued.Tokens -> outcome.tokens

                Issued.DeviceRevoked -> {
                    reusedRefreshTokens.increment()
                    throw InvalidCredentials()
                }
            }
        }
    }

    private suspend fun issue(source: Source): Issued {
        val deviceRecord = when (val check = source.check()) {
            is DeviceCheck.Found -> check.device
            DeviceCheck.NotFound -> throw InvalidCredentials()
            DeviceCheck.RefreshTokenReused -> return Issued.DeviceRevoked
        }
        val now = Clock.System.now()
        accountDataSource.recordActivity(
            authId = deviceRecord.authRecord.id,
            at = now,
            unlessRecordedSince = now - ACTIVITY_PRECISION,
        )
        val accessToken = createAccessToken(deviceRecord)
        val refreshToken = OpaqueRefreshToken.generate()
        when (source) {
            is Source.RefreshToken -> deviceDataSource.rotateRefreshToken(
                deviceRecord.deviceInfo.id,
                refreshToken.id,
                refreshToken.secretHash,
                now + REFRESH_TTL,
            )

            is Source.InitialAuthentication -> deviceDataSource.attachRefreshTokenToDevice(
                deviceRecord.deviceInfo.id,
                refreshToken.id,
                refreshToken.secretHash,
                now + REFRESH_TTL,
            )
        }
        return Issued.Tokens(AuthTokens(accessToken, refreshToken.token))
    }

    private suspend fun createAccessToken(record: Device): String = tokenIssuer.issue(ACCESS_TTL) {
        withJWTId(Uuid.random().toString())
            .withAudience(AppLevelConstants.domainName)
            .withClaim(AuthClaim.AUTH_ID.key, record.authRecord.id.toString())
            .withClaim(AuthClaim.USER_ID.key, record.authRecord.userId.toString())
            .withClaim(AuthClaim.DEVICE_ID.key, record.deviceInfo.id.toString())
    }

    private suspend fun Source.check(): DeviceCheck {
        return when (this) {
            is Source.RefreshToken -> validateRefreshToken(token)

            is Source.InitialAuthentication -> {
                deviceDataSource.getDeviceByAuthIdAndUUIDForUpdate(authId, deviceUUID)?.let {
                    DeviceCheck.Found(it)
                } ?: DeviceCheck.NotFound
            }
        }
    }

    private suspend fun validateRefreshToken(token: String): DeviceCheck {
        val refreshToken = OpaqueRefreshToken.parse(token) ?: return DeviceCheck.NotFound
        val device = deviceDataSource.getDeviceByRefreshTokenIdForUpdate(refreshToken.id) ?: return DeviceCheck.NotFound
        val currentDeviceToken = device.deviceInfo.refreshToken ?: return DeviceCheck.NotFound
        val previousDeviceToken = device.deviceInfo.previousRefreshToken

        if (previousDeviceToken != null && OpaqueRefreshToken.matches(refreshToken, previousDeviceToken)) {
            deviceSignOut.signOut(device)
            return DeviceCheck.RefreshTokenReused
        }
        if (OpaqueRefreshToken.matches(refreshToken, currentDeviceToken)) {
            return DeviceCheck.Found(device)
        }
        return DeviceCheck.NotFound
    }

    private sealed interface DeviceCheck {
        class Found(val device: Device) : DeviceCheck
        data object NotFound : DeviceCheck
        data object RefreshTokenReused : DeviceCheck
    }

    private sealed interface Issued {
        class Tokens(val tokens: AuthTokens) : Issued
        data object DeviceRevoked : Issued
    }

    companion object {
        const val REFRESH_TOKEN_REUSED_METRIC = "auth.refresh_token.reused"
        private val ACCESS_TTL = 5.minutes
        private val REFRESH_TTL = 7.days
        private val ACTIVITY_PRECISION = 1.days
    }
}
