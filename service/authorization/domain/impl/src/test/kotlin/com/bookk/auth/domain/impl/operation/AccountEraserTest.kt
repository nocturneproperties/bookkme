package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.api.identification.entity.DeviceInfo
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.auth.domain.datasource.ErasedAccountDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AuthEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class AccountEraserTest {

    private class SutFixture {
        val accountDataSource = mockk<AccountDataSource>(relaxUnitFun = true)
        val deviceDataSource = mockk<DeviceDataSource>()
        val erasedAccountDataSource = mockk<ErasedAccountDataSource>(relaxUnitFun = true)
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val sut = AccountEraser(accountDataSource, deviceDataSource, erasedAccountDataSource, eventProducer)
    }

    @Test
    fun `should record the erasure so a restore can run it again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val userId = Uuid.random()
        coEvery { fixture.deviceDataSource.getDevices(authId) } returns emptyList()

        whenn()
        fixture.sut.erase(authId, userId)

        then()
        coVerify(exactly = 1) { fixture.erasedAccountDataSource.record(userId) }
    }

    @Test
    fun `should delete the account and tell other services about the user and each device`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authRecord = Authentication.stub()
        val deviceUUID = Uuid.random()
        val device = Device(
            authRecord = authRecord,
            deviceInfo = DeviceInfo(id = Uuid.random(), deviceUUID = deviceUUID, refreshToken = null, previousRefreshToken = null, deviceName = "Phone", isSignedIn = true),
        )
        coEvery { fixture.deviceDataSource.getDevices(authRecord.id) } returns listOf(device)

        whenn()
        fixture.sut.erase(authRecord.id, authRecord.userId)

        then()
        coVerify(exactly = 1) { fixture.accountDataSource.deleteAuthorization(authRecord.id) }
        coVerify(exactly = 1) { fixture.eventProducer.send(match<AuthEvent.UserDeleted> { it.userId == authRecord.userId }, any()) }
        coVerify(exactly = 1) { fixture.eventProducer.send(match<AuthEvent.DeviceDeleted> { it.deviceUuid == deviceUUID }, any()) }
    }
}
