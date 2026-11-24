package com.bookk.auth.domain.impl.operation.token

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.api.identification.entity.DeviceInfo
import com.bookk.auth.domain.api.token.entity.SafeRefreshToken
import com.bookk.auth.domain.api.token.entity.UnsafeRefreshToken
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Error.InvalidCredentials
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Source
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.auth.domain.impl.operation.DeviceSignOut
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AuthEvent
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import library.signing.TokenIssuer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GenerateAuthTokenImplTest {

    private class SutFixture {
        val deviceDataSource = mockk<DeviceDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val tokenIssuer = mockk<TokenIssuer>()
        val accountDataSource = mockk<AccountDataSource>(relaxUnitFun = true)
        val meterRegistry = SimpleMeterRegistry()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)

        val sut = GenerateAuthTokenImpl(
            deviceDataSource,
            DeviceSignOut(deviceDataSource, eventProducer),
            transactionManager,
            tokenIssuer,
            accountDataSource,
            meterRegistry,
        )

        fun reusedTokens(): Double {
            return meterRegistry.find(GenerateAuthTokenImpl.REFRESH_TOKEN_REUSED_METRIC).counter()?.count() ?: 0.0
        }
    }

    private fun device(
        refreshToken: SafeRefreshToken?,
        previousRefreshToken: SafeRefreshToken? = null,
    ): Device = Device(
        authRecord = Authentication(id = Uuid.random(), userId = Uuid.random(), uuid = Uuid.random()),
        deviceInfo = DeviceInfo(
            id = Uuid.random(),
            deviceUUID = Uuid.random(),
            refreshToken = refreshToken,
            previousRefreshToken = previousRefreshToken,
            deviceName = "device",
            isSignedIn = true,
        ),
    )

    private fun UnsafeRefreshToken.toSafe() = SafeRefreshToken(id, secretHash)

    @Test
    fun `should rotate the refresh token on successful refresh`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val refreshToken = OpaqueRefreshToken.generate()
        val deviceRecord = device(refreshToken = refreshToken.toSafe())

        with(fixture) {
            coEvery { tokenIssuer.issue(any(), any()) } returns "access-token"
            coEvery { deviceDataSource.getDeviceByRefreshTokenIdForUpdate(refreshToken.id) } returns deviceRecord
            coEvery { deviceDataSource.rotateRefreshToken(any(), any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(Source.RefreshToken(refreshToken.token))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.deviceDataSource.rotateRefreshToken(deviceRecord.deviceInfo.id, any(), any()) }
        coVerify(exactly = 0) { fixture.deviceDataSource.attachRefreshTokenToDevice(any(), any(), any()) }
        coVerify(exactly = 0) { fixture.deviceDataSource.deleteTokenFromDevice(any()) }
    }

    @Test
    fun `should lock the device before any other statement of the refresh`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val refreshToken = OpaqueRefreshToken.generate()
        val deviceRecord = device(refreshToken = refreshToken.toSafe())
        with(fixture) {
            coEvery { tokenIssuer.issue(any(), any()) } returns "access-token"
            coEvery { deviceDataSource.getDeviceByRefreshTokenIdForUpdate(refreshToken.id) } returns deviceRecord
            coEvery { deviceDataSource.rotateRefreshToken(any(), any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        fixture.sut.invoke(Source.RefreshToken(refreshToken.token))

        then()
        coVerifyOrder {
            fixture.deviceDataSource.getDeviceByRefreshTokenIdForUpdate(refreshToken.id)
            fixture.accountDataSource.recordActivity(deviceRecord.authRecord.id, any(), any())
            fixture.deviceDataSource.rotateRefreshToken(deviceRecord.deviceInfo.id, any(), any())
        }
    }

    @Test
    fun `should revoke the device and fail when an already rotated-out refresh token is replayed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val rotatedOutToken = OpaqueRefreshToken.generate()
        val currentToken = OpaqueRefreshToken.generate()
        val deviceRecord = device(
            refreshToken = currentToken.toSafe(),
            previousRefreshToken = rotatedOutToken.toSafe(),
        )
        var deviceRevoked = false

        with(fixture) {
            coEvery { deviceDataSource.getDeviceByRefreshTokenIdForUpdate(rotatedOutToken.id) } returns deviceRecord
            coEvery { deviceDataSource.deleteTokenFromDevice(deviceRecord.deviceInfo.id) } answers {
                deviceRevoked = true
            }
            transactionManager.mockTransaction(onRollback = { deviceRevoked = false })
        }

        whenn()
        val result = fixture.sut.invoke(Source.RefreshToken(rotatedOutToken.token))

        then()
        assertTrue(result.exceptionOrNull() is InvalidCredentials)
        assertTrue(deviceRevoked)
        coVerify(exactly = 0) { fixture.deviceDataSource.rotateRefreshToken(any(), any(), any()) }
    }

    @Test
    fun `should publish DeviceSignedOut when a rotated-out refresh token is replayed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val rotatedOutToken = OpaqueRefreshToken.generate()
        val deviceRecord = device(
            refreshToken = OpaqueRefreshToken.generate().toSafe(),
            previousRefreshToken = rotatedOutToken.toSafe(),
        )
        with(fixture) {
            coEvery { deviceDataSource.getDeviceByRefreshTokenIdForUpdate(rotatedOutToken.id) } returns deviceRecord
            coEvery { deviceDataSource.deleteTokenFromDevice(deviceRecord.deviceInfo.id) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        fixture.sut.invoke(Source.RefreshToken(rotatedOutToken.token))

        then()
        coVerify(exactly = 1) {
            fixture.eventProducer.send(
                match<AuthEvent.DeviceSignedOut> {
                    it.deviceUuid == deviceRecord.deviceInfo.deviceUUID && it.authId == deviceRecord.authRecord.id
                },
                any(),
            )
        }
    }

    @Test
    fun `should not publish DeviceSignedOut on a successful refresh`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val refreshToken = OpaqueRefreshToken.generate()
        val deviceRecord = device(refreshToken = refreshToken.toSafe())
        with(fixture) {
            coEvery { tokenIssuer.issue(any(), any()) } returns "access-token"
            coEvery { deviceDataSource.getDeviceByRefreshTokenIdForUpdate(refreshToken.id) } returns deviceRecord
            coEvery { deviceDataSource.rotateRefreshToken(any(), any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        fixture.sut.invoke(Source.RefreshToken(refreshToken.token))

        then()
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AuthEvent.DeviceSignedOut>(), any()) }
    }

    @Test
    fun `should count a replayed rotated-out refresh token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val rotatedOutToken = OpaqueRefreshToken.generate()
        val deviceRecord = device(
            refreshToken = OpaqueRefreshToken.generate().toSafe(),
            previousRefreshToken = rotatedOutToken.toSafe(),
        )
        with(fixture) {
            coEvery { deviceDataSource.getDeviceByRefreshTokenIdForUpdate(rotatedOutToken.id) } returns deviceRecord
            coEvery { deviceDataSource.deleteTokenFromDevice(deviceRecord.deviceInfo.id) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        fixture.sut.invoke(Source.RefreshToken(rotatedOutToken.token))

        then()
        assertEquals(1.0, fixture.reusedTokens())
    }

    @Test
    fun `should not count a refresh token that is merely unknown`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceRecord = device(refreshToken = OpaqueRefreshToken.generate().toSafe())
        val unknownToken = OpaqueRefreshToken.generate()
        with(fixture) {
            coEvery { deviceDataSource.getDeviceByRefreshTokenIdForUpdate(unknownToken.id) } returns deviceRecord
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(Source.RefreshToken(unknownToken.token))

        then()
        assertTrue(result.exceptionOrNull() is InvalidCredentials)
        assertEquals(0.0, fixture.reusedTokens())
        coVerify(exactly = 0) { fixture.deviceDataSource.deleteTokenFromDevice(any()) }
    }

    @Test
    fun `should fail with invalid credentials when token is malformed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()

        whenn()
        val result = fixture.sut.invoke(Source.RefreshToken("not-a-valid-token"))

        then()
        assertEquals(InvalidCredentials().message, result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { fixture.deviceDataSource.getDeviceByRefreshTokenIdForUpdate(any()) }
    }

    @Test
    fun `should record the account's activity when a refresh token is used`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val refreshToken = OpaqueRefreshToken.generate()
        val deviceRecord = device(refreshToken = refreshToken.toSafe())
        val recordedAt = slot<Instant>()
        val unlessRecordedSince = slot<Instant>()
        with(fixture) {
            coEvery { tokenIssuer.issue(any(), any()) } returns "access-token"
            coEvery { deviceDataSource.getDeviceByRefreshTokenIdForUpdate(refreshToken.id) } returns deviceRecord
            coEvery { deviceDataSource.rotateRefreshToken(any(), any(), any()) } returns Unit
            coEvery {
                accountDataSource.recordActivity(
                    deviceRecord.authRecord.id,
                    capture(recordedAt),
                    capture(unlessRecordedSince),
                )
            } returns Unit
            transactionManager.mockTransaction()
        }
        val startedAt = Clock.System.now()

        whenn()
        fixture.sut.invoke(Source.RefreshToken(refreshToken.token))

        then()
        assertTrue(recordedAt.captured >= startedAt)
        assertEquals(recordedAt.captured - 1.days, unlessRecordedSince.captured)
    }

    @Test
    fun `should record the account's activity on sign-in`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceRecord = device(refreshToken = null)
        with(fixture) {
            coEvery { tokenIssuer.issue(any(), any()) } returns "access-token"
            coEvery {
                deviceDataSource.getDeviceByAuthIdAndUUIDForUpdate(
                    deviceRecord.authRecord.id,
                    deviceRecord.deviceInfo.deviceUUID,
                )
            } returns deviceRecord
            coEvery { deviceDataSource.attachRefreshTokenToDevice(any(), any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        fixture.sut.invoke(Source.InitialAuthentication(deviceRecord.authRecord.id, deviceRecord.deviceInfo.deviceUUID))

        then()
        coVerify(exactly = 1) { fixture.accountDataSource.recordActivity(deviceRecord.authRecord.id, any(), any()) }
    }

    @Test
    fun `should not record activity when the refresh token is unknown`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val refreshToken = OpaqueRefreshToken.generate()
        with(fixture) {
            coEvery { deviceDataSource.getDeviceByRefreshTokenIdForUpdate(refreshToken.id) } returns null
            transactionManager.mockTransaction()
        }

        whenn()
        fixture.sut.invoke(Source.RefreshToken(refreshToken.token))

        then()
        coVerify(exactly = 0) { fixture.accountDataSource.recordActivity(any(), any(), any()) }
    }

    @Test
    fun `should refuse a refresh token of a device that signed out`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val refreshToken = OpaqueRefreshToken.generate()
        val signedOutDevice = device(refreshToken = null)
        with(fixture) {
            coEvery { deviceDataSource.getDeviceByRefreshTokenIdForUpdate(refreshToken.id) } returns signedOutDevice
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(Source.RefreshToken(refreshToken.token))

        then()
        assertTrue(result.exceptionOrNull() is InvalidCredentials)
        assertEquals(0.0, fixture.reusedTokens())
        coVerify(exactly = 0) { fixture.tokenIssuer.issue(any(), any()) }
        coVerify(exactly = 0) { fixture.deviceDataSource.rotateRefreshToken(any(), any(), any()) }
        coVerify(exactly = 0) { fixture.accountDataSource.recordActivity(any(), any(), any()) }
    }

    @Test
    fun `should refuse to sign in on a device the account does not have`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val deviceUuid = Uuid.random()
        with(fixture) {
            coEvery { deviceDataSource.getDeviceByAuthIdAndUUIDForUpdate(authId, deviceUuid) } returns null
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(Source.InitialAuthentication(authId, deviceUuid))

        then()
        assertTrue(result.exceptionOrNull() is InvalidCredentials)
        coVerify(exactly = 0) { fixture.tokenIssuer.issue(any(), any()) }
        coVerify(exactly = 0) { fixture.deviceDataSource.attachRefreshTokenToDevice(any(), any(), any()) }
        coVerify(exactly = 0) { fixture.accountDataSource.recordActivity(any(), any(), any()) }
    }
}
