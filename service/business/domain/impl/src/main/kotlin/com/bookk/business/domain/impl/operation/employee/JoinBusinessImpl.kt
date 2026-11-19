package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.entity.EmployeeInvitation
import com.bookk.business.domain.api.employee.entity.EmployeeInvitationStatus
import com.bookk.business.domain.api.employee.operation.JoinBusiness
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.business.domain.datasource.EmployeeInvitationDataSource
import com.bookk.business.domain.datasource.RowLock
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.onConstraintFailure
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.api.event.BusinessEvent
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.UserSnapshot
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class JoinBusinessImpl(
    private val invitationDataSource: EmployeeInvitationDataSource,
    private val employeeDataSource: EmployeeDataSource,
    private val businessDataSource: BusinessDataSource,
    private val userClient: UserClient,
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer,
) : JoinBusiness {
    override suspend fun invoke(requestUserId: Uuid, code: String): Result<Employee> {
        if (code.isBlank()) return Result.failure(JoinBusiness.Error.EmptyInvitationCode())
        val codeHash = EmployeeInvitationCode.hash(code)
        return runSuspendCatching {
            val businessId = transactionManager.transactionOrThrow { findInvitation(codeHash).businessId }
            val requestUser = userClient.getUserById(requestUserId).getOrThrow()
            transactionManager.transactionOrThrow { join(requestUser, codeHash, businessId) }
        }.onConstraintFailure {
            throw JoinBusiness.Error.EmployeeExist()
        }
    }

    private suspend fun findInvitation(codeHash: String): EmployeeInvitation {
        return invitationDataSource.getInvitationByCodeHash(codeHash) ?: throw Error.NotFound()
    }

    private suspend fun join(requestUser: UserSnapshot, codeHash: String, businessId: Uuid): Employee {
        val requestUserId = requestUser.id
        val locked = businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) ?: throw Error.NotFound()
        val invitation = findInvitation(codeHash)
        if (invitation.status != EmployeeInvitationStatus.PENDING) {
            throw JoinBusiness.Error.InvitationAlreadyProcessed()
        }
        if (employeeDataSource.getEmployeeByUserId(invitation.businessId, requestUserId) != null) {
            throw JoinBusiness.Error.EmployeeExist()
        }
        if (employeeDataSource.countActiveEmployees(invitation.businessId) >= locked.limits.maxEmployees) {
            throw JoinBusiness.Error.EmployeesLimitReached()
        }
        if (!invitationDataSource.redeemInvitation(invitation.id)) {
            throw JoinBusiness.Error.InvitationAlreadyProcessed()
        }
        val business = businessDataSource.getBusinessById(invitation.businessId) ?: throw Error.NotFound()

        val employee = employeeDataSource.createEmployee(
            Employee(
                id = Uuid.random(),
                businessId = invitation.businessId,
                name = requestUser.name,
                lastName = requestUser.lastName,
                phone = requestUser.phone,
                email = requestUser.email,
                userId = requestUserId,
                services = emptyList(),
                schedule = business.schedule.workingWeek(),
                createdAt = Clock.System.now(),
                permissions = BusinessPermissions.VIEW_ONLY,
                suspendedAt = null,
            ),
        )
        eventProducer.send(
            BusinessEvent.EmployeeInvitationRedeemed(
                inviterUserId = invitation.invitedBy,
                employeeUserId = employee.userId,
                employeeName = employee.name,
                businessId = business.id,
                businessName = business.name,
            ),
        )
        eventProducer.send(
            BusinessEvent.EmployeePermissionsChanged(
                employeeUserId = employee.userId,
                businessId = business.id,
                permissions = employee.permissions,
            ),
        )
        return employee
    }
}
