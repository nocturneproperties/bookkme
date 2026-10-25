package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.employee.entity.EmployeeInvitation
import com.bookk.business.domain.api.employee.entity.EmployeeInvitationStatus
import com.bookk.business.domain.api.employee.operation.RevokeEmployeeInvitation
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.EmployeeInvitationDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class RevokeEmployeeInvitationImplTest {

    private val requestUserId = Uuid.random()
    private val businessId = Uuid.random()

    private class SutFixture {
        val invitationDataSource = mockk<EmployeeInvitationDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = RevokeEmployeeInvitationImpl(invitationDataSource, businessPermissionDataSource, transactionManager)

        init {
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.EMPLOYEES) } returns ResourcePermission(view = false, update = true, delete = false)
        }

        fun grantPermission(permission: ResourcePermission) {
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.EMPLOYEES) } returns permission
        }
    }

    @Test
    fun `should revoke pending invitation when caller is owner`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val invitation = EmployeeInvitation.stub(businessId = businessId)
        coEvery { fixture.invitationDataSource.getInvitation(businessId, invitation.id) } returns invitation
        coEvery { fixture.invitationDataSource.revokeInvitation(invitation.id) } returns true

        whenn()
        val result = fixture.sut(requestUserId, businessId, invitation.id)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.invitationDataSource.revokeInvitation(invitation.id) }
    }

    @Test
    fun `should return failure when caller has no owner permission for the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        fixture.grantPermission(ResourcePermission(view = true, update = false, delete = false))
        val id = Uuid.random()

        whenn()
        val result = fixture.sut(requestUserId, businessId, id)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.invitationDataSource.getInvitation(any(), any()) }
        coVerify(exactly = 0) { fixture.invitationDataSource.revokeInvitation(any()) }
    }

    @Test
    fun `should return failure when caller has no permission record for the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        fixture.grantPermission(ResourcePermission.NONE)
        val id = Uuid.random()

        whenn()
        val result = fixture.sut(requestUserId, businessId, id)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should return failure when invitation does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val id = Uuid.random()
        coEvery { fixture.invitationDataSource.getInvitation(businessId, id) } returns null

        whenn()
        val result = fixture.sut(requestUserId, businessId, id)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        coVerify(exactly = 0) { fixture.invitationDataSource.revokeInvitation(any()) }
    }

    @Test
    fun `should return failure when invitation is already processed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val invitation = EmployeeInvitation.stub(businessId = businessId, status = EmployeeInvitationStatus.REDEEMED)
        coEvery { fixture.invitationDataSource.getInvitation(businessId, invitation.id) } returns invitation

        whenn()
        val result = fixture.sut(requestUserId, businessId, invitation.id)

        then()
        assertTrue(result.exceptionOrNull() is RevokeEmployeeInvitation.Error.InvitationAlreadyProcessed)
        coVerify(exactly = 0) { fixture.invitationDataSource.revokeInvitation(any()) }
    }

    @Test
    fun `should return failure when invitation was concurrently processed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val invitation = EmployeeInvitation.stub(businessId = businessId)
        coEvery { fixture.invitationDataSource.getInvitation(businessId, invitation.id) } returns invitation
        coEvery { fixture.invitationDataSource.revokeInvitation(invitation.id) } returns false

        whenn()
        val result = fixture.sut(requestUserId, businessId, invitation.id)

        then()
        assertTrue(result.exceptionOrNull() is RevokeEmployeeInvitation.Error.InvitationAlreadyProcessed)
    }
}
