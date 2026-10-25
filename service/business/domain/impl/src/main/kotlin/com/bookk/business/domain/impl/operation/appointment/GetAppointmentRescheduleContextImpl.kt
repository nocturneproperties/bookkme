package com.bookk.business.domain.impl.operation.appointment

import com.bookk.business.domain.api.appointment.entity.AppointmentRescheduleContext
import com.bookk.business.domain.api.appointment.operation.GetAppointmentRescheduleContext
import com.bookk.business.domain.api.appointment.operation.GetAppointmentRescheduleContext.Error
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.business.domain.impl.operation.getServicesExpanded
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetAppointmentRescheduleContextImpl(
    private val employeeDataSource: EmployeeDataSource,
    private val serviceDataSource: ServiceDataSource,
    private val transactionManager: TransactionManager
) : GetAppointmentRescheduleContext {
    override suspend fun invoke(
        businessId: Uuid,
        employeeId: Uuid?,
        serviceIds: List<Uuid>
    ): Result<AppointmentRescheduleContext> = transactionManager.transaction {
        AppointmentRescheduleContext(
            employee = employeeId?.let { resolveEmployee(businessId, it) },
            services = resolveServices(businessId, serviceIds)
        )
    }

    private suspend fun resolveEmployee(businessId: Uuid, employeeId: Uuid): Employee {
        val employee = employeeDataSource.getEmployee(businessId, employeeId) ?: throw Error.EmployeeNotFound()
        if (employee.isSuspended) throw Error.EmployeeSuspended()
        return employee
    }

    private suspend fun resolveServices(businessId: Uuid, serviceIds: List<Uuid>): List<Service> {
        if (serviceIds.isEmpty()) return emptyList()
        return serviceDataSource.getServicesExpanded(businessId, serviceIds) ?: throw Error.ServiceNotFound()
    }
}
