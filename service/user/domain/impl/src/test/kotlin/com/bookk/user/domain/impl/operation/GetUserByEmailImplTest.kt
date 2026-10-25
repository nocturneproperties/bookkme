package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.entity.EmailBody
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.operation.GetUserByEmail
import com.bookk.user.domain.datasource.UserDataSource
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
internal class GetUserByEmailImplTest {

    private class SutFixture {
        val userDataSource = mockk<UserDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetUserByEmailImpl(userDataSource, transactionManager)
    }

    @Test
    fun `should return user when found by email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val email = "john@example.com"
        val user = User.stub(email = email)
        val body = EmailBody(email)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.getUserByEmail(email) } returns user
        }

        whenn()
        val result = fixture.sut.invoke(body)

        then()
        assertTrue(result.isSuccess)
        assertEquals(user, result.getOrNull())
    }

    @Test
    fun `should return UserNotFound when no user exists with that email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val body = EmailBody("unknown@example.com")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.getUserByEmail(body.email) } returns null
        }

        whenn()
        val result = fixture.sut.invoke(body)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GetUserByEmail.Error.UserNotFound)
    }
}
