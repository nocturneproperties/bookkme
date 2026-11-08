package com.bookk.user.domain.impl.operation

import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.user.client.api.event.UserEvent
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.operation.ChangeUserEmail
import com.bookk.user.domain.datasource.UserDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class ChangeUserEmailImplTest {

    private class SutFixture {
        val userDataSource = mockk<UserDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val sut = ChangeUserEmailImpl(userDataSource, transactionManager, eventProducer)
        val sentInTransaction = mutableListOf<Boolean>()

        init {
            transactionManager.mockTransaction()
            coEvery { eventProducer.send(any<UserEvent.Updated>(), any()) } coAnswers {
                sentInTransaction += transactionManager.isInTransaction()
            }
        }
    }

    @Test
    fun `should store the verified email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        coEvery { fixture.userDataSource.changeEmail(userId, NEW_EMAIL, any()) } returns User.stub(id = userId, email = NEW_EMAIL)

        whenn()
        val result = fixture.sut.invoke(userId, NEW_EMAIL)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 1) { fixture.userDataSource.changeEmail(userId, NEW_EMAIL, any()) }
    }

    @Test
    fun `should publish the profile with its new email in the same transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val changed = User.stub(id = userId, name = "Jane", lastName = "Smith", email = NEW_EMAIL, phone = "+10000000000")
        coEvery { fixture.userDataSource.changeEmail(userId, NEW_EMAIL, any()) } returns changed

        whenn()
        fixture.sut.invoke(userId, NEW_EMAIL)

        then()
        coVerify(exactly = 1) {
            fixture.eventProducer.send(
                match<UserEvent.Updated> {
                    it.userId == userId &&
                        it.name == "Jane" &&
                        it.lastName == "Smith" &&
                        it.email == NEW_EMAIL &&
                        it.phone == "+10000000000"
                },
                any(),
            )
        }
        assertTrue(fixture.sentInTransaction.single())
    }

    @Test
    fun `should return UserNotFound and publish nothing when the user does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        coEvery { fixture.userDataSource.changeEmail(userId, NEW_EMAIL, any()) } returns null

        whenn()
        val result = fixture.sut.invoke(userId, NEW_EMAIL)

        then()
        assertTrue(result.exceptionOrNull() is ChangeUserEmail.Error.UserNotFound) { result.toString() }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<UserEvent.Updated>(), any()) }
    }

    @Test
    fun `should return EmailTaken when another account holds the email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        coEvery { fixture.userDataSource.changeEmail(userId, NEW_EMAIL, any()) } throws
            Error.UniqueConstraintFailed("duplicate", IllegalStateException())

        whenn()
        val result = fixture.sut.invoke(userId, NEW_EMAIL)

        then()
        assertTrue(result.exceptionOrNull() is ChangeUserEmail.Error.EmailTaken) { result.toString() }
    }

    private companion object {
        const val NEW_EMAIL = "new@example.com"
    }
}
