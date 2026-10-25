package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.employee.entity.EmployeeInvitationStatus
import com.bookk.business.domain.api.employee.operation.RevokeEmployeeInvitation
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.EmployeeInvitationDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class RevokeEmployeeInvitationImpl(
    private val invitationDataSource: EmployeeInvitationDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager
) : RevokeEmployeeInvitation {
    override suspend fun invoke(requestUserId: Uuid, businessId: Uuid, id: Uuid): Result<Unit> =
        transactionManager.transaction {
            businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.EMPLOYEES)
                .assert(PermissionAction.UPDATE)
            val invitation = invitationDataSource.getInvitation(businessId, id) ?: throw Error.NotFound()
            if (invitation.status != EmployeeInvitationStatus.PENDING) {
                throw RevokeEmployeeInvitation.Error.InvitationAlreadyProcessed()
            }
            if (!invitationDataSource.revokeInvitation(id)) {
                throw RevokeEmployeeInvitation.Error.InvitationAlreadyProcessed()
            }
        }
}
