package com.bookk.auth.domain.impl.operation.token

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.demo.entity.DemoAccountPolicy
import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Error.DemoExpired
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
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
        val account = deviceRecord.authRecord
        if (account.isDemoEndedAt(now)) throw DemoExpired()
        accountDataSource.recordActivity(
            authId = account.id,
            at = now,
            unlessRecordedSince = now - ACTIVITY_PRECISION,
        )
        val accessToken = createAccessToken(deviceRecord, account.accessTtlAt(now))
        val refreshToken = OpaqueRefreshToken.generate()
        val refreshExpiresAt = account.refreshExpiryAt(now)
        when (source) {
            is Source.RefreshToken -> deviceDataSource.rotateRefreshToken(
                deviceRecord.deviceInfo.id,
                refreshToken.id,
                refreshToken.secretHash,
                refreshExpiresAt,
            )

            is Source.InitialAuthentication -> deviceDataSource.attachRefreshTokenToDevice(
                deviceRecord.deviceInfo.id,
                refreshToken.id,
                refreshToken.secretHash,
                refreshExpiresAt,
            )
        }
        return Issued.Tokens(AuthTokens(accessToken, refreshToken.token))
    }

    private suspend fun createAccessToken(record: Device, ttl: Duration): String = tokenIssuer.issue(ttl) {
        val claims = withJWTId(Uuid.random().toString())
            .withAudience(AppLevelConstants.domainName)
            .withClaim(AuthClaim.AUTH_ID.key, record.authRecord.id.toString())
            .withClaim(AuthClaim.USER_ID.key, record.authRecord.userId.toString())
            .withClaim(AuthClaim.DEVICE_ID.key, record.deviceInfo.id.toString())
        if (record.authRecord.isDemo) claims.withClaim(AuthClaim.DEMO.key, true) else claims
    }

    private fun Authentication.isDemoEndedAt(now: Instant): Boolean {
        val end = demoExpiresAt ?: return false
        return now >= end
    }

    private fun Authentication.accessTtlAt(now: Instant): Duration {
        val end = demoExpiresAt ?: return ACCESS_TTL
        return minOf(ACCESS_TTL, end - now)
    }

    private fun Authentication.refreshExpiryAt(now: Instant): Instant {
        val end = demoExpiresAt ?: return now + REFRESH_TTL
        return DemoAccountPolicy.upgradeDeadline(end)
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
