package com.bookk.user.domain.impl.operation

import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.user.client.api.event.UserEvent
import com.bookk.user.domain.api.entity.TelegramUsernameChange
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.operation.ChangeTelegramUsername
import com.bookk.user.domain.datasource.UserDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class ChangeTelegramUsernameImplTest {

    private class SutFixture {
        val userDataSource = mockk<UserDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val eventProducer = mockk<StandardEventProducer>()
        val sut = ChangeTelegramUsernameImpl(userDataSource, transactionManager, eventProducer)
        val published = slot<UserEvent.Updated>()
        val sentInTransaction = mutableListOf<Boolean>()

        init {
            transactionManager.mockTransaction()
            coEvery { eventProducer.send(capture(published), any()) } coAnswers {
                sentInTransaction += transactionManager.isInTransaction()
            }
        }
    }

    @Test
    fun `should publish the profile with its new Telegram username in the same transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val changed = User.stub(telegramUserId = 42, telegramUsername = "ann_renamed")
        coEvery { fixture.userDataSource.changeTelegramUsername(changed.id, "ann_renamed", any()) } returns
            TelegramUsernameChange(changed, changed = true)

        whenn()
        val result = fixture.sut.invoke(changed.id, "ann_renamed")

        then()
        assertTrue(result.isSuccess) { result.toString() }
        assertEquals(changed.id, fixture.published.captured.userId)
        assertEquals("ann_renamed", fixture.published.captured.telegramUsername)
        assertEquals(42L, fixture.published.captured.telegramUserId)
        assertEquals(listOf(true), fixture.sentInTransaction)
    }

    @Test
    fun `should publish a removed Telegram username`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val changed = User.stub(telegramUsername = null)
        coEvery { fixture.userDataSource.changeTelegramUsername(changed.id, null, any()) } returns
            TelegramUsernameChange(changed, changed = true)

        whenn()
        val result = fixture.sut.invoke(changed.id, null)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        assertEquals(null, fixture.published.captured.telegramUsername)
    }

    @Test
    fun `should publish nothing when the Telegram username is unchanged`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(telegramUsername = "ann_lee")
        coEvery { fixture.userDataSource.changeTelegramUsername(user.id, "ann_lee", any()) } returns
            TelegramUsernameChange(user, changed = false)

        whenn()
        val result = fixture.sut.invoke(user.id, "ann_lee")

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<UserEvent.Updated>(), any()) }
    }

    @Test
    fun `should fail with UserNotFound when the user is missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        coEvery { fixture.userDataSource.changeTelegramUsername(userId, any(), any()) } returns null

        whenn()
        val result = fixture.sut.invoke(userId, "ann_lee")

        then()
        assertTrue(result.exceptionOrNull() is ChangeTelegramUsername.Error.UserNotFound) { result.toString() }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<UserEvent.Updated>(), any()) }
    }

    @Test
    fun `should fail with UsernameInvalid without opening a transaction`() = runUnitTest {
        given()
        val userDataSource = mockk<UserDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = ChangeTelegramUsernameImpl(userDataSource, transactionManager, mockk())

        whenn()
        val result = sut.invoke(Uuid.random(), "a".repeat(300))

        then()
        assertTrue(result.exceptionOrNull() is ChangeTelegramUsername.Error.UsernameInvalid) { result.toString() }
        coVerify(exactly = 0) { transactionManager.transaction<Any>(any()) }
    }
}
