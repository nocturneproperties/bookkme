package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.BusinessAppointmentsEnabled
import com.bookk.appointments.domain.api.operation.EnableAppointmentsForBusiness
import com.bookk.appointments.domain.api.operation.GetClientBusinessesAppointmentsStatus
import com.bookk.appointments.domain.api.operation.IsAppointmentsEnabled
import com.bookk.appointments.domain.impl.di.AppointmentsScope
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Routing
import io.ktor.server.routing.application
import io.ktor.server.routing.openapi.describe

fun Routing.appointmentInit() {
    authenticate {
        /**
         * Summary: Enable appointments
         * Description: Enable appointments for business. The business information and its working schedule are read from the business service
         * Tag: appointment
         * Security: jwt
         * Response: 204 application/x-protobuf Created appointment entity
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Business is unknown to the business service
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Appointment errors<br>PLUGIN_ALREADY_ENABLED (300009) Appointment plugin already enabled
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/appointments/enable-appointments-for-business.md
         */
        post<Api.Appointment.Enabled> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val enableAppointments by application.injectScoped<EnableAppointmentsForBusiness>(AppointmentsScope)

            call.respondWith(
                enableAppointments(
                    userId = principal.userId,
                    businessId = it.businessId
                )
            )
        }

        /**
         * Summary: Check if enabled
         * Description: Check whether appointments plugin is enabled or not
         * Tag: appointment
         * Security: jwt
         * Response: 200 application/x-protobuf [kotlin.Boolean] true or false
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         */
        get<Api.Appointment.Enabled> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val isEnabled by application.injectScoped<IsAppointmentsEnabled>(AppointmentsScope)

            call.respondWith(
                isEnabled(
                    userId = principal.userId,
                    businessId = it.businessId
                )
            )
        }

        /**
         * Summary: Check appointments enabled for client businesses
         * Description: Appointment-enabled status for every business the calling user has a client relationship with
         * Tag: appointment
         * Security: jwt
         */
        get<Api.Appointment.EnabledForClientBusinesses> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getClientBusinessesAppointmentsStatus by application.injectScoped<GetClientBusinessesAppointmentsStatus>(AppointmentsScope)

            call.respondWith(getClientBusinessesAppointmentsStatus(userId = principal.userId))
        }.describe {
            responses {
                response(HttpStatusCode.OK.value) {
                    schema = jsonSchema<List<BusinessAppointmentsEnabled>>()
                    description = "Appointment-enabled status for each business the user has a client relation to"
                    ContentType.Application.ProtoBuf()
                }
            }
        }
    }
}
