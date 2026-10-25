package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.employee.entity.EmployeeInvitation
import com.bookk.business.domain.api.employee.entity.EmployeeInvitationStatus
import com.bookk.business.domain.datasource.EmployeeInvitationDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetEmployeeInvitationsImplTest {

    private class SutFixture {
        val invitationDataSource = mockk<EmployeeInvitationDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetEmployeeInvitationsImpl(invitationDataSource, transactionManager)
    }

    @Test
    fun `should return all invitations sent by the caller regardless of status`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val invitations = listOf(
            EmployeeInvitation.stub(businessId = businessId, invitedBy = userId, status = EmployeeInvitationStatus.PENDING),
            EmployeeInvitation.stub(businessId = businessId, invitedBy = userId, status = EmployeeInvitationStatus.REDEEMED)
        )
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { invitationDataSource.getInvitationsByInviter(businessId, userId) } returns invitations
        }

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(invitations, result.getOrNull())
    }

    @Test
    fun `should return empty list when caller has not sent any invitations`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { invitationDataSource.getInvitationsByInviter(businessId, userId) } returns emptyList()
        }

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }
}
