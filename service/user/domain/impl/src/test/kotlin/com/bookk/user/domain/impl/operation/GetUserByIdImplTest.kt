package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.operation.GetUserById
import com.bookk.user.domain.datasource.UserDataSource
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetUserByIdImplTest {

    private class SutFixture {
        val userDataSource = mockk<UserDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetUserByIdImpl(userDataSource, transactionManager)
    }

    @Test
    fun `should return user when found by id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val user = User.stub(id = userId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.getUserById(userId) } returns user
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(user, result.getOrNull())
    }

    @Test
    fun `should return UserNotFound when no user exists with that id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.getUserById(userId) } returns null
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GetUserById.Error.UserNotFound)
    }
}
