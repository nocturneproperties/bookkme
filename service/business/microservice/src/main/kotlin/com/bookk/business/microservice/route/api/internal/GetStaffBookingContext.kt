package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.appointment.entity.StaffBookingContextRequest
import com.bookk.business.domain.api.appointment.operation.GetStaffBookingContext
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.getStaffBookingContext() {
    /**
     * Summary: Get staff booking context
     * Description: Resolve the employee, an existing client of this business and the services for an employee booking that client. Read only, nothing is created or linked. Used by appointments to build a verified appointment snapshot for an instant appointment
     * Tag: internal
     * Body: application/x-protobuf [com.bookk.business.domain.api.appointment.entity.StaffBookingContextRequest]
     * Response: 200 application/x-protobuf [com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext] Resolved employee, client and services
     * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Get staff booking context errors<br>BUSINESS_EMPLOYEE_NOT_EXISTS (200024) Employee with this id is missing<br>BUSINESS_CLIENT_NOT_EXISTS (200006) Client not found
     * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Get staff booking context errors<br>BUSINESS_QUOTE_SERVICE_NOT_FOUND (200013) One or more services not found<br>BUSINESS_QUOTE_EMPTY_SERVICE_LIST (200014) Service list must not be empty<br>BUSINESS_EMPLOYEE_SUSPENDED (200033) Employee is suspended and cannot be booked
     * See: docs/operations/business/get-staff-booking-context.md
     */
    post<Api.Internal.Business.Id.StaffBookingContext> { resource ->
        val body = call.receive<StaffBookingContextRequest>()
        val getStaffBookingContext by application.injectScoped<GetStaffBookingContext>(BusinessScope)

        call.respondWith(
            getStaffBookingContext(
                businessId = resource.parent.id,
                employeeId = body.employeeId,
                clientId = body.clientId,
                serviceIds = body.serviceIds
            )
        )
    }
}
