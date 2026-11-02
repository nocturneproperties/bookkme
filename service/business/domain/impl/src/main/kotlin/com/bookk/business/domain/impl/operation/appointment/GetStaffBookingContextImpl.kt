package com.bookk.business.domain.impl.operation.appointment

import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.business.domain.api.appointment.operation.GetStaffBookingContext
import com.bookk.business.domain.api.appointment.operation.GetStaffBookingContext.Error
import com.bookk.business.domain.api.client.entity.toRemote
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.business.domain.impl.operation.getServicesExpanded
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetStaffBookingContextImpl(
    private val employeeDataSource: EmployeeDataSource,
    private val clientDataSource: ClientDataSource,
    private val serviceDataSource: ServiceDataSource,
    private val transactionManager: TransactionManager
) : GetStaffBookingContext {
    override suspend fun invoke(
        businessId: Uuid,
        employeeId: Uuid,
        clientId: Uuid,
        serviceIds: List<Uuid>
    ): Result<AppointmentBookingContext> {
        if (serviceIds.isEmpty()) return Result.failure(Error.EmptyServiceList())
        return transactionManager.transaction {
            val employee = employeeDataSource.getEmployee(businessId, employeeId) ?: throw Error.EmployeeNotFound()
            if (employee.isSuspended) throw Error.EmployeeSuspended()
            val client = clientDataSource.getClientById(businessId, clientId) ?: throw Error.ClientNotFound()
            val services = serviceDataSource.getServicesExpanded(businessId, serviceIds) ?: throw Error.ServiceNotFound()

            AppointmentBookingContext(employee = employee, client = client.toRemote(), services = services)
        }
    }
}
