package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContextRequest
import com.bookk.business.domain.api.appointment.operation.GetAppointmentBookingContext
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.getAppointmentBookingContext() {
    /**
     * Summary: Get appointment booking context
     * Description: Resolve the employee, services and the calling user's client record for a booking, creating an integrated client from the user's profile on their first booking with this business. Used by other services to build a verified appointment snapshot
     * Tag: internal
     * Body: application/x-protobuf [com.bookk.business.domain.api.appointment.entity.AppointmentBookingContextRequest]
     * Response: 200 application/x-protobuf [com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext] Resolved employee, client and services
     * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Get appointment booking context errors<br>BUSINESS_EMPLOYEE_NOT_EXISTS (200024) Employee with this id is missing
     * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Get appointment booking context errors<br>BUSINESS_QUOTE_SERVICE_NOT_FOUND (200013) One or more services not found<br>BUSINESS_QUOTE_EMPTY_SERVICE_LIST (200014) Service list must not be empty<br>BUSINESS_EMPLOYEE_SUSPENDED (200033) Employee is suspended and cannot be booked
     */
    post<Api.Internal.Business.Id.AppointmentBookingContext> { resource ->
        val body = call.receive<AppointmentBookingContextRequest>()
        val getAppointmentBookingContext by application.injectScoped<GetAppointmentBookingContext>(BusinessScope)

        call.respondWith(
            getAppointmentBookingContext(
                businessId = resource.parent.id,
                employeeId = body.employeeId,
                userId = body.userId,
                serviceIds = body.serviceIds
            )
        )
    }
}
