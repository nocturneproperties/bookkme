package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetOwnedBusinessMemberIdsImplTest {

    private class SutFixture {
        val employeeDataSource = mockk<EmployeeDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val readInTransaction = mutableListOf<Boolean>()
        val sut = GetOwnedBusinessMemberIdsImpl(employeeDataSource, transactionManager)

        init {
            transactionManager.mockTransaction()
        }
    }

    @Test
    fun `should return the other members of the business the user owns`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ownerId = Uuid.random()
        val members = listOf(Uuid.random(), Uuid.random())
        coEvery { fixture.employeeDataSource.getOwnedBusinessMemberUserIds(ownerId) } coAnswers {
            fixture.readInTransaction += fixture.transactionManager.isInTransaction()
            members
        }

        whenn()
        val result = fixture.sut(ownerId)

        then()
        assertEquals(members, result.getOrNull())
        assertEquals(listOf(true), fixture.readInTransaction)
    }

    @Test
    fun `should fail when the members cannot be read`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val error = IllegalStateException("database down")
        coEvery { fixture.employeeDataSource.getOwnedBusinessMemberUserIds(any()) } throws error

        whenn()
        val result = fixture.sut(Uuid.random())

        then()
        assertSame(error, result.exceptionOrNull())
    }
}
