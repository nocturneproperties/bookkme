package com.bookk.notifications.domain.impl

import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.entity.Device
import com.bookk.notifications.domain.datasource.DeviceDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class UpdateDeviceLanguageTest {

    private class SutFixture {
        val deviceDataSource = mockk<DeviceDataSource>()
        val sut = UpdateDeviceLanguage(deviceDataSource)
    }

    @Test
    fun `should update device language successfully`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        with(fixture) {
            coEvery { deviceDataSource.updateLanguage(deviceUuid, Language.UK) } returns Device.stub(deviceId = deviceUuid, language = Language.UK)
        }

        whenn()
        val result = fixture.sut.invoke(deviceUuid, Language.UK)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.deviceDataSource.updateLanguage(deviceUuid, Language.UK) }
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        with(fixture) {
            coEvery { deviceDataSource.updateLanguage(deviceUuid, Language.EN) } throws RuntimeException("db error")
        }

        whenn()
        val result = fixture.sut.invoke(deviceUuid, Language.EN)

        then()
        assertTrue(result.isFailure)
    }
}
