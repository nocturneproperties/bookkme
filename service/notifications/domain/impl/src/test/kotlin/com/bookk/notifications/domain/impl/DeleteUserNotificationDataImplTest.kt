package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.encryption.SubjectKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeleteUserNotificationDataImplTest {

    private class SutFixture {
        val notificationSettingsDataSource = mockk<NotificationSettingsDataSource>()
        val notificationTargetDataSource = mockk<NotificationTargetDataSource>()
        val subjectKeys = mockk<SubjectKeys>(relaxUnitFun = true)
        val transactionManager = mockk<TransactionManager>()
        val sut =
            DeleteUserNotificationDataImpl(
                notificationSettingsDataSource,
                notificationTargetDataSource,
                subjectKeys,
                transactionManager,
            )
    }

    @Test
    fun `should delete notification settings and targets for the user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { notificationSettingsDataSource.deleteByUserId(userId) } returns Unit
            coEvery { notificationTargetDataSource.deleteByUserId(userId) } returns Unit
        }

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.notificationSettingsDataSource.deleteByUserId(userId) }
        coVerify(exactly = 1) { fixture.notificationTargetDataSource.deleteByUserId(userId) }
    }

    @Test
    fun `should shred the user's key in the same transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val shreddedInTransaction = mutableListOf<Boolean>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { notificationSettingsDataSource.deleteByUserId(userId) } returns Unit
            coEvery { notificationTargetDataSource.deleteByUserId(userId) } returns Unit
            coEvery { subjectKeys.shred(userId) } coAnswers
                { shreddedInTransaction += transactionManager.isInTransaction() }
        }

        whenn()
        fixture.sut(userId)

        then()
        assertEquals(listOf(true), shreddedInTransaction)
    }
}
