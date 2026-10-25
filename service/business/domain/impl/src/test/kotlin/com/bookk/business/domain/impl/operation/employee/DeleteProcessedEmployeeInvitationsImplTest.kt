package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.datasource.EmployeeInvitationDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal class DeleteProcessedEmployeeInvitationsImplTest {

    private class SutFixture {
        val invitationDataSource = mockk<EmployeeInvitationDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DeleteProcessedEmployeeInvitationsImpl(invitationDataSource, transactionManager)
    }

    @Test
    fun `should delete processed invitations older than the 30 day retention window`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val cutoff = slot<Instant>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { invitationDataSource.deleteProcessedInvitations(capture(cutoff)) } returns Unit
        }

        whenn()
        val before = Clock.System.now()
        val result = fixture.sut.invoke()
        val after = Clock.System.now()

        then()
        assertTrue(result.isSuccess)
        assertTrue(cutoff.captured in (before - 30.days)..(after - 30.days))
    }

    @Test
    fun `should return failure when datasource fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { invitationDataSource.deleteProcessedInvitations(any()) } throws RuntimeException("DB error")
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message == "DB error")
    }
}
