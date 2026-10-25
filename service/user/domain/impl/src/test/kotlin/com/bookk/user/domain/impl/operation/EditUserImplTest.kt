package com.bookk.user.domain.impl.operation

import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.user.client.api.event.UserEvent
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.entity.UserEditModel
import com.bookk.user.domain.api.operation.EditUser
import com.bookk.user.domain.datasource.UserDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class EditUserImplTest {

    private class SutFixture {
        val userDataSource = mockk<UserDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val sut = EditUserImpl(userDataSource, transactionManager, eventProducer)
    }

    private val userId = Uuid.random()

    private fun editModel(
        firstName: String? = null,
        lastName: String? = null,
        email: String? = null,
        phone: String? = null
    ) = UserEditModel(id = null, firstName = firstName, lastName = lastName, email = email, phone = phone)

    @Test
    fun `should edit user successfully when datasource confirms update`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val editModel = editModel(firstName = "Jane", lastName = "Smith")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.updateUser(userId, editModel, any()) } returns User.stub(id = userId)
        }

        whenn()
        val result = fixture.sut.invoke(userId, editModel)

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should return UserNotFound when the user does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val editModel = editModel(firstName = "Jane")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.updateUser(userId, editModel, any()) } returns null
        }

        whenn()
        val result = fixture.sut.invoke(userId, editModel)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is EditUser.Error.UserNotFound)
    }

    @Test
    fun `should publish the updated profile so replicas can follow it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val editModel = editModel(firstName = "Jane", lastName = "Smith", email = "jane@example.com")
        val updated = User(id = userId, name = "Jane", lastName = "Smith", email = "jane@example.com", phone = null)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.updateUser(userId, editModel, any()) } returns updated
        }

        whenn()
        val result = fixture.sut.invoke(userId, editModel)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.eventProducer.send(
                match<UserEvent.Updated> {
                    it.userId == userId &&
                        it.name == "Jane" &&
                        it.lastName == "Smith" &&
                        it.email == "jane@example.com"
                },
                any()
            )
        }
    }

    @Test
    fun `should publish the phone when the profile has one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val edit = editModel(phone = "+10000000000")
        val updated = User(
            id = userId, name = "Jane", lastName = "Smith",
            email = "jane@example.com", phone = "+10000000000"
        )
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.updateUser(userId, edit, any()) } returns updated
        }

        whenn()
        fixture.sut.invoke(userId, edit)

        then()
        coVerify(exactly = 1) {
            fixture.eventProducer.send(match<UserEvent.Updated> { it.phone == "+10000000000" }, any())
        }
    }

    @Test
    fun `should publish a null phone when the profile has none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val edit = editModel(firstName = "Jane")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.updateUser(userId, edit, any()) } returns User.stub(id = userId)
        }

        whenn()
        fixture.sut.invoke(userId, edit)

        then()
        coVerify(exactly = 1) {
            fixture.eventProducer.send(match<UserEvent.Updated> { it.phone == null }, any())
        }
    }

    @Test
    fun `should not publish anything when the user does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val editModel = editModel(firstName = "Jane")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.updateUser(userId, editModel, any()) } returns null
        }

        whenn()
        fixture.sut.invoke(userId, editModel)

        then()
        coVerify(exactly = 0) { fixture.eventProducer.send(any<UserEvent.Updated>(), any()) }
    }
}
