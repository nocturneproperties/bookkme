package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.mockedTransactionManager
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.datasource.DeviceDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ClearPushNotificationTokenTest {

    private class SutFixture {
        val deviceDataSource = mockk<DeviceDataSource>()
        val transactionManager = mockedTransactionManager()
        val sut = ClearPushNotificationToken(deviceDataSource, transactionManager)
    }

    @Test
    fun `should clear the token registered before the sign out`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val deviceUuid = Uuid.random()
        val signedOutAt = Instant.fromEpochMilliseconds(1_700_000_000_000)
        coEvery { fixture.deviceDataSource.clearTokenRegisteredBefore(authId, deviceUuid, signedOutAt) } returns Unit

        whenn()
        val result = fixture.sut.invoke(authId, deviceUuid, signedOutAt)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.deviceDataSource.clearTokenRegisteredBefore(authId, deviceUuid, signedOutAt) }
    }

    @Test
    fun `should clear the token inside a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        var clearedInTransaction = false
        coEvery { fixture.deviceDataSource.clearTokenRegisteredBefore(any(), any(), any()) } coAnswers {
            clearedInTransaction = fixture.transactionManager.isInTransaction()
        }

        whenn()
        fixture.sut.invoke(Uuid.random(), Uuid.random(), Instant.fromEpochMilliseconds(0))

        then()
        assertTrue(clearedInTransaction)
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.deviceDataSource.clearTokenRegisteredBefore(any(), any(), any()) } throws
            RuntimeException("db error")

        whenn()
        val result = fixture.sut.invoke(Uuid.random(), Uuid.random(), Instant.fromEpochMilliseconds(0))

        then()
        assertTrue(result.isFailure)
    }
}
