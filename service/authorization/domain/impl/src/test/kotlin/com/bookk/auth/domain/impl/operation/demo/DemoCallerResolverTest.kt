package com.bookk.auth.domain.impl.operation.demo

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.demo.entity.DemoCaller
import com.bookk.auth.domain.api.demo.operation.DemoUpgradeError
import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.api.identification.entity.DeviceInfo
import com.bookk.auth.domain.api.token.entity.SafeRefreshToken
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.auth.domain.impl.operation.token.OpaqueRefreshToken
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DemoCallerResolverTest {

    private class SutFixture {
        val deviceDataSource = mockk<DeviceDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DemoCallerResolver(deviceDataSource, transactionManager)
        val refreshToken = OpaqueRefreshToken.generate()

        init {
            transactionManager.mockTransaction()
        }

        fun device(demoExpiresAt: Instant?): Device {
            return Device.stub(
                authRecord = Authentication.stub(demoExpiresAt = demoExpiresAt),
                deviceInfo = DeviceInfo.stub(refreshToken = SafeRefreshToken(refreshToken.id, refreshToken.secretHash)),
            )
        }
    }

    @Test
    fun `should resolve the demo account and device of an access token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val device = fixture.device(demoExpiresAt = Clock.System.now() + 10.days)
        coEvery { fixture.deviceDataSource.getDeviceById(device.deviceInfo.id) } returns device

        whenn()
        val session = fixture.sut.resolve(DemoCaller.AccessToken(device.authRecord.id, device.deviceInfo.id))

        then()
        assertEquals(device.authRecord, session.account)
        assertEquals(device.deviceInfo.deviceUUID, session.deviceUuid)
    }

    @Test
    fun `should refuse an access token whose device belongs to another account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val device = fixture.device(demoExpiresAt = Clock.System.now() + 10.days)
        coEvery { fixture.deviceDataSource.getDeviceById(device.deviceInfo.id) } returns device

        whenn()
        val result =
            runSuspendCatching { fixture.sut.resolve(DemoCaller.AccessToken(Uuid.random(), device.deviceInfo.id)) }

        then()
        assertTrue(result.exceptionOrNull() is GenerateAuthToken.Error.InvalidCredentials) { result.toString() }
    }

    @Test
    fun `should resolve a demo device by its refresh token in the grace period after the demo`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val device = fixture.device(demoExpiresAt = Clock.System.now() - 3.days)
        coEvery { fixture.deviceDataSource.getDeviceByRefreshTokenIdForUpdate(fixture.refreshToken.id) } returns device

        whenn()
        val session = fixture.sut.resolve(DemoCaller.RefreshToken(fixture.refreshToken.token))

        then()
        assertEquals(device.authRecord, session.account)
    }

    @Test
    fun `should refuse a refresh token that is not the device's current one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val device = fixture.device(demoExpiresAt = Clock.System.now() + 10.days)
        val other = OpaqueRefreshToken.generate()
        coEvery { fixture.deviceDataSource.getDeviceByRefreshTokenIdForUpdate(other.id) } returns device

        whenn()
        val result = runSuspendCatching { fixture.sut.resolve(DemoCaller.RefreshToken(other.token)) }

        then()
        assertTrue(result.exceptionOrNull() is GenerateAuthToken.Error.InvalidCredentials) { result.toString() }
    }

    @Test
    fun `should refuse a malformed refresh token`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = runSuspendCatching { fixture.sut.resolve(DemoCaller.RefreshToken("not-a-token")) }

        then()
        assertTrue(result.exceptionOrNull() is GenerateAuthToken.Error.InvalidCredentials) { result.toString() }
    }

    @Test
    fun `should refuse an account that is not a demo`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val device = fixture.device(demoExpiresAt = null)
        coEvery { fixture.deviceDataSource.getDeviceById(device.deviceInfo.id) } returns device

        whenn()
        val result = runSuspendCatching {
            fixture.sut.resolve(DemoCaller.AccessToken(device.authRecord.id, device.deviceInfo.id))
        }

        then()
        assertTrue(result.exceptionOrNull() is DemoUpgradeError.NotDemoAccount) { result.toString() }
    }

    @Test
    fun `should refuse a demo whose upgrade grace period has passed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val device = fixture.device(demoExpiresAt = Clock.System.now() - 8.days)
        coEvery { fixture.deviceDataSource.getDeviceByRefreshTokenIdForUpdate(fixture.refreshToken.id) } returns device

        whenn()
        val result = runSuspendCatching { fixture.sut.resolve(DemoCaller.RefreshToken(fixture.refreshToken.token)) }

        then()
        assertTrue(result.exceptionOrNull() is GenerateAuthToken.Error.DemoExpired) { result.toString() }
    }
}
