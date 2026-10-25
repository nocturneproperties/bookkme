package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.datasource.EmployeeInvitationDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class ExpireEmployeeInvitationsImplTest {

    private class SutFixture {
        val invitationDataSource = mockk<EmployeeInvitationDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = ExpireEmployeeInvitationsImpl(invitationDataSource, transactionManager)
    }

    @Test
    fun `should expire pending invitations older than the expiry window`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { invitationDataSource.expireOldInvitations(any<Instant>()) } returns Unit
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.invitationDataSource.expireOldInvitations(any()) }
    }

    @Test
    fun `should return failure when datasource fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { invitationDataSource.expireOldInvitations(any<Instant>()) } throws RuntimeException("DB error")
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message == "DB error")
    }
}
