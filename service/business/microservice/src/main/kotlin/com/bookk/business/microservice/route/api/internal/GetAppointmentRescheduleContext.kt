package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.appointment.entity.AppointmentRescheduleContextRequest
import com.bookk.business.domain.api.appointment.operation.GetAppointmentRescheduleContext
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.getAppointmentRescheduleContext() {
    /**
     * Summary: Get appointment reschedule context
     * Description: Resolve the employee (when given) and services (in request order, repeated per count) for rescheduling an appointment of this business. Unlike the booking context it never resolves or creates a client. Used by other services to build a verified appointment snapshot
     * Tag: internal
     * Body: application/x-protobuf [com.bookk.business.domain.api.appointment.entity.AppointmentRescheduleContextRequest]
     * Response: 200 application/x-protobuf [com.bookk.business.domain.api.appointment.entity.AppointmentRescheduleContext] Resolved employee and services
     * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Get appointment reschedule context errors<br>BUSINESS_EMPLOYEE_NOT_EXISTS (200024) Employee with this id is missing
     * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Get appointment reschedule context errors<br>BUSINESS_QUOTE_SERVICE_NOT_FOUND (200013) One or more services not found<br>BUSINESS_EMPLOYEE_SUSPENDED (200033) Employee is suspended and cannot be booked
     */
    post<Api.Internal.Business.Id.AppointmentRescheduleContext> { resource ->
        val body = call.receive<AppointmentRescheduleContextRequest>()
        val getAppointmentRescheduleContext by application.injectScoped<GetAppointmentRescheduleContext>(BusinessScope)

        call.respondWith(
            getAppointmentRescheduleContext(
                businessId = resource.parent.id,
                employeeId = body.employeeId,
                serviceIds = body.serviceIds
            )
        )
    }
}
