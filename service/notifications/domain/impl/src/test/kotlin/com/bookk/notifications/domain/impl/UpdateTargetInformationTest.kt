package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.GetNotificationSettings
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.api.entity.NotificationChannelSettings
import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import com.bookk.notifications.domain.impl.UpdateTargetInformation.Target
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import library.encryption.test.InMemorySubjectKeys
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class UpdateTargetInformationTest {

    private class SutFixture {
        val getNotificationSettings = mockk<GetNotificationSettings>()
        val targetDataSource = mockk<NotificationTargetDataSource>()
        val notificationSettingsDataSource = mockk<NotificationSettingsDataSource>()
        val subjectKeys = spyk(InMemorySubjectKeys())
        val transactionManager = mockk<TransactionManager>()
        val sut = UpdateTargetInformation(
            getNotificationSettings,
            targetDataSource,
            notificationSettingsDataSource,
            subjectKeys,
            transactionManager,
        )
    }

    private val userId = Uuid.random()
    private val updatedAt = Instant.fromEpochMilliseconds(1000)

    private fun SutFixture.settingsFound(telegram: NotificationChannelSettings = offeredTelegram()) {
        transactionManager.mockTransaction()
        coEvery { getNotificationSettings.invoke(userId) } returns
            Result.success(NotificationSettings.stub(userId = userId))
        coEvery { notificationSettingsDataSource.getByUserId(userId) } returns
            NotificationSettings.stub(userId = userId, channels = listOf(telegram))
        coEvery { notificationSettingsDataSource.upsert(any<NotificationChannelSettings>()) } just Runs
    }

    private fun offeredTelegram(): NotificationChannelSettings {
        return NotificationChannelSettings.stub(channel = CommunicationChannel.TELEGRAM, availableToClients = true)
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
        val result = fixture.sut.invoke(userId, listOf(Target.Email("user@example.com")), updatedAt)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.targetDataSource.insertEmail(any(), any(), any()) }
        coVerify(exactly = 0) { fixture.targetDataSource.hasEmail(any()) }
    }

    @Test
    fun `should insert the email target when no row exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            settingsFound()
            coEvery { targetDataSource.updateEmail(userId, "user@example.com", updatedAt) } returns false
            coEvery { targetDataSource.hasEmail(userId) } returns false
            coEvery { targetDataSource.insertEmail(userId, "user@example.com", updatedAt) } just Runs
        }

        whenn()
        val result = fixture.sut.invoke(userId, listOf(Target.Email("user@example.com")), updatedAt)

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
            coEvery { targetDataSource.hasEmail(userId) } returns true
        }

        whenn()
        val result = fixture.sut.invoke(userId, listOf(Target.Email("stale@example.com")), updatedAt)

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
            coEvery { targetDataSource.updateTelegram(userId, "123456789") } returns true
        }

        whenn()
        val result = fixture.sut.invoke(userId, listOf(Target.Telegram(123456789)), updatedAt)

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
            coEvery { targetDataSource.updateTelegram(userId, "123456789") } returns false
            coEvery { targetDataSource.hasTelegram(userId) } returns false
            coEvery { targetDataSource.insertTelegram(userId, "123456789") } just Runs
        }

        whenn()
        val result = fixture.sut.invoke(userId, listOf(Target.Telegram(123456789)), updatedAt)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.targetDataSource.insertTelegram(userId, "123456789") }
    }

    @Test
    fun `should not insert the telegram target when a row already exists but was not updated`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            settingsFound()
            coEvery { targetDataSource.updateTelegram(userId, "123456789") } returns false
            coEvery { targetDataSource.hasTelegram(userId) } returns true
        }

        whenn()
        val result = fixture.sut.invoke(userId, listOf(Target.Telegram(123456789)), updatedAt)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.targetDataSource.insertTelegram(any(), any()) }
    }

    @Test
    fun `should offer the Telegram channel, still off, once a Telegram target is saved`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val telegram = NotificationChannelSettings.stub(
            channel = CommunicationChannel.TELEGRAM,
            enabled = false,
            availableToClients = false,
        )
        with(fixture) {
            settingsFound(telegram)
            coEvery { targetDataSource.updateTelegram(userId, "123456789") } returns false
            coEvery { targetDataSource.hasTelegram(userId) } returns false
            coEvery { targetDataSource.insertTelegram(userId, "123456789") } just Runs
        }

        whenn()
        val result = fixture.sut.invoke(userId, listOf(Target.Telegram(123456789)), updatedAt)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 1) {
            fixture.notificationSettingsDataSource.upsert(telegram.copy(availableToClients = true))
        }
    }

    @Test
    fun `should not rewrite a Telegram channel that is already offered`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            settingsFound()
            coEvery { targetDataSource.updateTelegram(userId, "123456789") } returns true
        }

        whenn()
        fixture.sut.invoke(userId, listOf(Target.Telegram(123456789)), updatedAt)

        then()
        coVerify(exactly = 0) { fixture.notificationSettingsDataSource.upsert(any<NotificationChannelSettings>()) }
    }

    @Test
    fun `should not offer the Telegram channel when only the email target is saved`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            settingsFound(offeredTelegram().copy(availableToClients = false))
            coEvery { targetDataSource.updateEmail(userId, "user@example.com", updatedAt) } returns true
        }

        whenn()
        fixture.sut.invoke(userId, listOf(Target.Email("user@example.com")), updatedAt)

        then()
        coVerify(exactly = 0) { fixture.notificationSettingsDataSource.upsert(any<NotificationChannelSettings>()) }
    }

    @Test
    fun `should save the email and Telegram targets in one transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            settingsFound()
            coEvery { targetDataSource.updateEmail(userId, "user@example.com", updatedAt) } returns true
            coEvery { targetDataSource.updateTelegram(userId, "123456789") } returns false
            coEvery { targetDataSource.hasTelegram(userId) } returns false
            coEvery { targetDataSource.insertTelegram(userId, "123456789") } just Runs
        }

        whenn()
        val result = fixture.sut.invoke(
            userId,
            listOf(Target.Email("user@example.com"), Target.Telegram(123456789)),
            updatedAt,
        )

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 1) { fixture.transactionManager.transaction<Unit>(any()) }
        coVerify(exactly = 1) { fixture.targetDataSource.updateEmail(userId, "user@example.com", updatedAt) }
        coVerify(exactly = 1) { fixture.targetDataSource.insertTelegram(userId, "123456789") }
    }

    @Test
    fun `should neither create settings nor store a target for a user already erased`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            subjectKeys.shred(userId)
        }

        whenn()
        val result = fixture.sut.invoke(userId, listOf(Target.Email("late@example.com")), updatedAt)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 0) { fixture.getNotificationSettings.invoke(any()) }
        coVerify(exactly = 0) { fixture.targetDataSource.updateEmail(any(), any(), any()) }
        coVerify(exactly = 0) { fixture.targetDataSource.insertEmail(any(), any(), any()) }
    }

    @Test
    fun `should lock the user's key before reading or creating the settings`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            settingsFound()
            coEvery { targetDataSource.updateEmail(userId, "user@example.com", updatedAt) } returns true
        }

        whenn()
        fixture.sut.invoke(userId, listOf(Target.Email("user@example.com")), updatedAt)

        then()
        coVerifyOrder {
            fixture.subjectKeys.isShredded(userId)
            fixture.getNotificationSettings.invoke(userId)
        }
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
        val result = fixture.sut.invoke(userId, listOf(Target.Email("e@example.com")), updatedAt)

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
        val result = fixture.sut.invoke(userId, listOf(Target.Email("e@example.com")), updatedAt)

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
        val result = fixture.sut.invoke(userId, listOf(Target.Telegram(123456789)), updatedAt)

        then()
        assertTrue(result.isFailure)
    }
}
