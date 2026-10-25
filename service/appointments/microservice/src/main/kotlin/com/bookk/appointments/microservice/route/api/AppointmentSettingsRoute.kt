package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.AppointmentSettingsUpdate
import com.bookk.appointments.domain.api.operation.EditSettings
import com.bookk.appointments.domain.api.operation.GetSettings
import com.bookk.appointments.domain.impl.di.AppointmentsScope
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.application

fun Routing.settings() {
    authenticate {
        /**
         * Summary: Get settings
         * Description: Get appointment settings
         * Tag: appointment
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.AppointmentSettings] Settings entity
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Settings not found or user is not allowed to read them
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         */
        get<Api.Appointment.Settings> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getSettings by application.injectScoped<GetSettings>(AppointmentsScope)

            call.respondWith(
                getSettings(
                    userId = principal.userId,
                    businessId = it.businessId
                )
            )
        }

        /**
         * Summary: Update settings
         * Description: Update appointment settings. The working schedule and day offs belong to the business and are updated through the business endpoint
         * Tag: appointment
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.appointments.domain.api.entity.AppointmentSettingsUpdate]
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.AppointmentSettings] Updated settings entity
         * Response: 400 application/x-protobuf Path business id does not match body business id
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Settings not found or user is not allowed to update them
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/appointments/edit-appointment-settings.md
         */
        put<Api.Appointment.Settings> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<AppointmentSettingsUpdate>()
            val editSettings by application.injectScoped<EditSettings>(AppointmentsScope)

            if (it.businessId != body.businessId) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
            } else {
                call.respondWith(
                    editSettings(
                        userId = principal.userId,
                        update = body
                    )
                )
            }
        }
    }
}
