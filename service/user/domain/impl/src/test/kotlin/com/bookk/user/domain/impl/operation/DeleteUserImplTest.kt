package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.datasource.UserDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeleteUserImplTest {

    private class SutFixture {
        val userDataSource = mockk<UserDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DeleteUserImpl(userDataSource, transactionManager)
    }

    @Test
    fun `should delete user successfully`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.deleteUser(userId) } returns Unit
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.userDataSource.deleteUser(userId) }
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.deleteUser(userId) } throws RuntimeException("db error")
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertTrue(result.isFailure)
    }
}
