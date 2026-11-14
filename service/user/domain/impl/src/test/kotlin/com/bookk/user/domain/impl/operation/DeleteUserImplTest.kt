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
import library.encryption.SubjectKeys
import library.export.DataExports
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeleteUserImplTest {

    private class SutFixture {
        val userDataSource = mockk<UserDataSource>()
        val dataExports = mockk<DataExports>()
        val subjectKeys = mockk<SubjectKeys>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DeleteUserImpl(userDataSource, dataExports, subjectKeys, transactionManager)
        val discardedInTransaction = mutableListOf<Boolean>()
        val shreddedInTransaction = mutableListOf<Boolean>()
        val userId = Uuid.random()

        init {
            transactionManager.mockTransaction()
            coEvery { userDataSource.deleteUser(userId) } returns Unit
            coEvery { dataExports.discard(userId) } coAnswers
                { discardedInTransaction += transactionManager.isInTransaction() }
            coEvery { subjectKeys.shred(userId) } coAnswers
                { shreddedInTransaction += transactionManager.isInTransaction() }
        }
    }

    @Test
    fun `should delete user successfully`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut.invoke(fixture.userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.userDataSource.deleteUser(fixture.userId) }
    }

    @Test
    fun `should delete the user's data export in the same transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.invoke(fixture.userId)

        then()
        assertEquals(listOf(true), fixture.discardedInTransaction)
    }

    @Test
    fun `should shred the user's key in the same transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.invoke(fixture.userId)

        then()
        assertEquals(listOf(true), fixture.shreddedInTransaction)
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.userDataSource.deleteUser(fixture.userId) } throws RuntimeException("db error")

        whenn()
        val result = fixture.sut.invoke(fixture.userId)

        then()
        assertTrue(result.isFailure)
    }
}
