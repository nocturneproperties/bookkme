package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.employee.entity.EmployeeInvitation
import com.bookk.business.domain.api.employee.entity.EmployeeInvitationStatus
import com.bookk.business.domain.api.employee.operation.CreateEmployeeInvitation
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.EmployeeInvitationDataSource
import com.bookk.business.domain.datasource.RowLock
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

internal class CreateEmployeeInvitationImpl(
    private val invitationDataSource: EmployeeInvitationDataSource,
    private val businessDataSource: BusinessDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager
) : CreateEmployeeInvitation {
    override suspend fun invoke(requestUserId: Uuid, businessId: Uuid): Result<EmployeeInvitation> {
        return transactionManager.transaction {
            businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) ?: throw Error.NotFound()
            businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.EMPLOYEES)
                .assert(PermissionAction.UPDATE)
            val pendingInvitations = invitationDataSource.countPendingInvitations(businessId)
            if (pendingInvitations >= CreateEmployeeInvitation.MAX_PENDING_INVITATIONS) {
                throw CreateEmployeeInvitation.Error.PendingInvitationsLimitReached()
            }
            val invitationsToday = invitationDataSource.countInvitationsCreatedSince(
                businessId,
                Clock.System.now() - DAILY_QUOTA_WINDOW
            )
            if (invitationsToday >= CreateEmployeeInvitation.MAX_INVITATIONS_PER_DAY) {
                throw CreateEmployeeInvitation.Error.DailyInvitationsLimitReached()
            }
            createInvitationWithUniqueCode(requestUserId, businessId)
        }
    }

    private suspend fun createInvitationWithUniqueCode(
        requestUserId: Uuid,
        businessId: Uuid,
        remainingAttempts: Int = MAX_CODE_ATTEMPTS
    ): EmployeeInvitation {
        val plainCode = EmployeeInvitationCode.generate()
        val invitation = EmployeeInvitation(
            id = Uuid.random(),
            businessId = businessId,
            invitedBy = requestUserId,
            code = EmployeeInvitationCode.hash(plainCode),
            status = EmployeeInvitationStatus.PENDING,
            createdAt = Clock.System.now()
        )
        return try {
            invitationDataSource.createInvitation(invitation).copy(code = plainCode)
        } catch (error: Error.UniqueConstraintFailed) {
            if (remainingAttempts <= 1) throw error
            createInvitationWithUniqueCode(requestUserId, businessId, remainingAttempts - 1)
        }
    }

    private companion object {
        const val MAX_CODE_ATTEMPTS = 5
        val DAILY_QUOTA_WINDOW = 24.hours
    }
}
