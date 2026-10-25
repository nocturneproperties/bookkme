package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.datasource.UserDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
internal class CreateUserImplTest {

    private class SutFixture {
        val userDataSource = mockk<UserDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = CreateUserImpl(userDataSource, transactionManager)
    }

    @Test
    fun `should insert user and return its id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.insertNewUser(user) } returns user
        }

        whenn()
        val result = fixture.sut.invoke(user)

        then()
        assertTrue(result.isSuccess)
        assertEquals(user.id, result.getOrNull()?.id)
        coVerify(exactly = 1) { fixture.userDataSource.insertNewUser(user) }
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.insertNewUser(user) } throws RuntimeException("constraint violation")
        }

        whenn()
        val result = fixture.sut.invoke(user)

        then()
        assertTrue(result.isFailure)
    }
}
