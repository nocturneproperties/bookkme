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
import com.bookk.user.domain.api.entity.ProfileName
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.operation.UpgradeDemoProfile
import com.bookk.user.domain.datasource.UserDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class UpgradeDemoProfileImplTest {

    private class SutFixture {
        val userDataSource = mockk<UserDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val sut = UpgradeDemoProfileImpl(userDataSource, transactionManager, eventProducer)
        val userId = Uuid.random()
        val name = ProfileName.stub(name = "Ann", lastName = "Lee")
        val sentInTransaction = mutableListOf<Boolean>()

        init {
            transactionManager.mockTransaction()
            coEvery { eventProducer.send(any<UserEvent.Updated>(), any()) } coAnswers {
                sentInTransaction += transactionManager.isInTransaction()
            }
        }
    }

    @Test
    fun `should store the verified email and the name in one write`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.userDataSource.changeEmailAndName(fixture.userId, EMAIL, fixture.name, any()) } returns
            User.stub(id = fixture.userId, name = "Ann", lastName = "Lee", email = EMAIL)

        whenn()
        val result = fixture.sut.invoke(fixture.userId, EMAIL, fixture.name)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 1) { fixture.userDataSource.changeEmailAndName(fixture.userId, EMAIL, fixture.name, any()) }
    }

    @Test
    fun `should publish the profile with its new email and name in the same transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val changed = User.stub(
            id = fixture.userId,
            name = "Ann",
            lastName = "Lee",
            email = EMAIL,
            phone = "+10000000000",
            telegramUserId = 123456789,
        )
        coEvery { fixture.userDataSource.changeEmailAndName(fixture.userId, EMAIL, fixture.name, any()) } returns
            changed

        whenn()
        fixture.sut.invoke(fixture.userId, EMAIL, fixture.name)

        then()
        coVerify(exactly = 1) {
            fixture.eventProducer.send(
                match<UserEvent.Updated> {
                    it.userId == fixture.userId &&
                        it.name == "Ann" &&
                        it.lastName == "Lee" &&
                        it.email == EMAIL &&
                        it.phone == "+10000000000" &&
                        it.telegramUserId == 123456789L
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
        coEvery { fixture.userDataSource.changeEmailAndName(fixture.userId, EMAIL, fixture.name, any()) } returns null

        whenn()
        val result = fixture.sut.invoke(fixture.userId, EMAIL, fixture.name)

        then()
        assertTrue(result.exceptionOrNull() is UpgradeDemoProfile.Error.UserNotFound) { result.toString() }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<UserEvent.Updated>(), any()) }
    }

    @Test
    fun `should return EmailTaken when another account holds the email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.userDataSource.changeEmailAndName(fixture.userId, EMAIL, fixture.name, any()) } throws
            Error.UniqueConstraintFailed("duplicate", IllegalStateException())

        whenn()
        val result = fixture.sut.invoke(fixture.userId, EMAIL, fixture.name)

        then()
        assertTrue(result.exceptionOrNull() is UpgradeDemoProfile.Error.EmailTaken) { result.toString() }
    }

    private companion object {
        const val EMAIL = "ann@example.com"
    }
}
