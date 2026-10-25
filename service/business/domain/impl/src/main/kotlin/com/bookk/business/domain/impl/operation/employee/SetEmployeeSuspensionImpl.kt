package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.operation.SetEmployeeSuspension
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import com.bookk.server.business.client.api.event.BusinessEvent
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class SetEmployeeSuspensionImpl(
    private val employeeDataSource: EmployeeDataSource,
    private val businessDataSource: BusinessDataSource,
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer
) : SetEmployeeSuspension {
    override suspend fun invoke(
        requestUserId: Uuid,
        businessId: Uuid,
        employeeId: Uuid,
        suspended: Boolean
    ): Result<Employee> = transactionManager.transaction {
        if (!businessDataSource.isOwner(requestUserId, businessId)) throw Error.OperationNotAllowed()
        val employee = employeeDataSource.getEmployee(businessId, employeeId) ?: throw Error.NotFound()
        if (businessDataSource.isOwner(employee.userId, businessId)) {
            throw SetEmployeeSuspension.Error.OwnerSuspensionNotAllowed()
        }
        if (employee.isSuspended == suspended) return@transaction employee
        val updated = employeeDataSource.setSuspendedAt(employee.id, if (suspended) Clock.System.now() else null)
        eventProducer.send(
            BusinessEvent.EmployeePermissionsChanged(
                employeeUserId = updated.userId,
                businessId = businessId,
                permissions = updated.permissions,
                suspended = updated.isSuspended
            )
        )
        updated
    }
}
