package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.GetNotificationSettings
import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import com.bookk.notifications.domain.impl.UpdateTargetInformation.Target
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class UpdateTargetInformationTest {

    private class SutFixture {
        val getNotificationSettings = mockk<GetNotificationSettings>()
        val targetDataSource = mockk<NotificationTargetDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = UpdateTargetInformation(getNotificationSettings, targetDataSource, transactionManager)
    }

    private val userId = Uuid.random()
    private val updatedAt = Instant.fromEpochMilliseconds(1000)

    private fun SutFixture.settingsFound() {
        transactionManager.mockTransaction()
        coEvery { getNotificationSettings.invoke(userId) } returns
            Result.success(NotificationSettings.stub(userId = userId))
    }

    @Test
    fun `should update the email target when a row already exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            settingsFound()
            coEvery { targetDataSource.updateEmail(userId, "user@example.com", updatedAt) } returns true
        }

        whenn()
        val result = fixture.sut.invoke(userId, Target.Email("user@example.com"), updatedAt)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.targetDataSource.insertEmail(any(), any(), any()) }
        coVerify(exactly = 0) { fixture.targetDataSource.getEmail(any()) }
    }

    @Test
    fun `should insert the email target when no row exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            settingsFound()
            coEvery { targetDataSource.updateEmail(userId, "user@example.com", updatedAt) } returns false
            coEvery { targetDataSource.getEmail(userId) } returns null
            coEvery { targetDataSource.insertEmail(userId, "user@example.com", updatedAt) } just Runs
        }

        whenn()
        val result = fixture.sut.invoke(userId, Target.Email("user@example.com"), updatedAt)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.targetDataSource.insertEmail(userId, "user@example.com", updatedAt) }
    }

    @Test
    fun `should not insert when the update was rejected as stale`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            settingsFound()
            coEvery { targetDataSource.updateEmail(userId, "stale@example.com", updatedAt) } returns false
            coEvery { targetDataSource.getEmail(userId) } returns "newer@example.com"
        }

        whenn()
        val result = fixture.sut.invoke(userId, Target.Email("stale@example.com"), updatedAt)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.targetDataSource.insertEmail(any(), any(), any()) }
    }

    @Test
    fun `should update the telegram target when a row already exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            settingsFound()
            coEvery { targetDataSource.updateTelegram(userId, "@user_tag") } returns true
        }

        whenn()
        val result = fixture.sut.invoke(userId, Target.Telegram("@user_tag"), updatedAt)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.targetDataSource.insertTelegram(any(), any()) }
        coVerify(exactly = 0) { fixture.targetDataSource.updateEmail(any(), any(), any()) }
    }

    @Test
    fun `should insert the telegram target when no row exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            settingsFound()
            coEvery { targetDataSource.updateTelegram(userId, "@user_tag") } returns false
            coEvery { targetDataSource.getTelegram(userId) } returns null
            coEvery { targetDataSource.insertTelegram(userId, "@user_tag") } just Runs
        }

        whenn()
        val result = fixture.sut.invoke(userId, Target.Telegram("@user_tag"), updatedAt)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.targetDataSource.insertTelegram(userId, "@user_tag") }
    }

    @Test
    fun `should return failure when getNotificationSettings fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { getNotificationSettings.invoke(userId) } returns Result.failure(RuntimeException("not found"))
        }

        whenn()
        val result = fixture.sut.invoke(userId, Target.Email("e@example.com"), updatedAt)

        then()
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { fixture.targetDataSource.updateEmail(any(), any(), any()) }
    }

    @Test
    fun `should return failure when the email update throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            settingsFound()
            coEvery { targetDataSource.updateEmail(userId, any(), any()) } throws RuntimeException("db error")
        }

        whenn()
        val result = fixture.sut.invoke(userId, Target.Email("e@example.com"), updatedAt)

        then()
        assertTrue(result.isFailure)
    }

    @Test
    fun `should return failure when the telegram update throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            settingsFound()
            coEvery { targetDataSource.updateTelegram(userId, any()) } throws RuntimeException("db error")
        }

        whenn()
        val result = fixture.sut.invoke(userId, Target.Telegram("@tag"), updatedAt)

        then()
        assertTrue(result.isFailure)
    }
}
