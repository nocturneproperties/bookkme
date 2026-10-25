package com.bookk.business.domain.datasource

import com.bookk.business.domain.api.employee.entity.EmployeeInvitation
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface EmployeeInvitationDataSource {
    suspend fun createInvitation(invitation: EmployeeInvitation): EmployeeInvitation
    suspend fun getInvitation(businessId: Uuid, id: Uuid): EmployeeInvitation?
    suspend fun getInvitationByCodeHash(codeHash: String): EmployeeInvitation?
    suspend fun countPendingInvitations(businessId: Uuid): Long
    suspend fun countInvitationsCreatedSince(businessId: Uuid, since: Instant): Long
    suspend fun getInvitationsByInviter(businessId: Uuid, invitedBy: Uuid): List<EmployeeInvitation>
    suspend fun redeemInvitation(id: Uuid): Boolean
    suspend fun revokeInvitation(id: Uuid): Boolean
    suspend fun expireOldInvitations(before: Instant)
    suspend fun deleteProcessedInvitations(before: Instant)
}
