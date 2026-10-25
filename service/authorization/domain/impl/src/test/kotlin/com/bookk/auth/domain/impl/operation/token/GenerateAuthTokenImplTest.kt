package com.bookk.auth.domain.impl.operation.token

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.api.identification.entity.DeviceInfo
import com.bookk.auth.domain.api.token.entity.SafeRefreshToken
import com.bookk.auth.domain.api.token.entity.UnsafeRefreshToken
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Error.InvalidCredentials
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Source
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.signing.TokenIssuer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GenerateAuthTokenImplTest {

    private class SutFixture {
        val deviceDataSource = mockk<DeviceDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val tokenIssuer = mockk<TokenIssuer>()

        val sut = GenerateAuthTokenImpl(
            deviceDataSource,
            transactionManager,
            tokenIssuer
        )
    }

    private fun device(refreshToken: SafeRefreshToken?, previousRefreshToken: SafeRefreshToken? = null): Device {
        return Device(
            authRecord = Authentication(id = Uuid.random(), userId = Uuid.random(), uuid = Uuid.random()),
            deviceInfo = DeviceInfo(
                id = Uuid.random(),
                deviceUUID = Uuid.random(),
                refreshToken = refreshToken,
                previousRefreshToken = previousRefreshToken,
                deviceName = "device",
                isSignedIn = true
            )
        )
    }

    private fun UnsafeRefreshToken.toSafe() = SafeRefreshToken(id, secretHash)

    @Test
    fun `should rotate the refresh token on successful refresh`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val refreshToken = OpaqueRefreshToken.generate()
        val deviceRecord = device(refreshToken = refreshToken.toSafe())

        with(fixture) {
            coEvery { tokenIssuer.issue(any(), any()) } returns "access-token"
            coEvery { deviceDataSource.getDeviceByRefreshTokenId(refreshToken.id) } returns deviceRecord
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
    fun `should revoke the device and fail when an already rotated-out refresh token is replayed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val rotatedOutToken = OpaqueRefreshToken.generate()
        val currentToken = OpaqueRefreshToken.generate()
        val deviceRecord = device(
            refreshToken = currentToken.toSafe(),
            previousRefreshToken = rotatedOutToken.toSafe()
        )

        with(fixture) {
            coEvery { deviceDataSource.getDeviceByRefreshTokenId(rotatedOutToken.id) } returns deviceRecord
            coEvery { deviceDataSource.deleteTokenFromDevice(deviceRecord.deviceInfo.id) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(Source.RefreshToken(rotatedOutToken.token))

        then()
        assertEquals(InvalidCredentials().message, result.exceptionOrNull()?.message)
        coVerify(exactly = 1) { fixture.deviceDataSource.deleteTokenFromDevice(deviceRecord.deviceInfo.id) }
        coVerify(exactly = 0) { fixture.deviceDataSource.rotateRefreshToken(any(), any(), any()) }
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
        coVerify(exactly = 0) { fixture.deviceDataSource.getDeviceByRefreshTokenId(any()) }
    }
}
