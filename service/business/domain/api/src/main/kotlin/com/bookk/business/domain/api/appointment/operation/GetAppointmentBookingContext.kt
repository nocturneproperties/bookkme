package com.bookk.business.domain.api.appointment.operation

import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface GetAppointmentBookingContext {
    suspend operator fun invoke(businessId: Uuid, employeeId: Uuid, userId: Uuid, serviceIds: List<Uuid>): Result<AppointmentBookingContext>

    sealed interface Error {
        class EmployeeNotFound : BusinessError(
            statusCode = HttpStatusCode.NotFound.value,
            code = BusinessErrorCodes.BUSINESS_EMPLOYEE_NOT_EXISTS,
            message = "Employee with this id is missing"
        ), Error

        class EmployeeSuspended : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_EMPLOYEE_SUSPENDED,
            message = "Employee is suspended and cannot be booked"
        ), Error

        class ServiceNotFound : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_QUOTE_SERVICE_NOT_FOUND,
            message = "One or more services not found"
        ), Error

        class EmptyServiceList : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_QUOTE_EMPTY_SERVICE_LIST,
            message = "Service list must not be empty"
        ), Error
    }
}
