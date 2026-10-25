package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.AppointmentCancellation
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestDraft
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest
import com.bookk.appointments.domain.api.operation.DeclineAppointmentRequest
import com.bookk.appointments.domain.api.operation.GetAppointmentRequests
import com.bookk.appointments.domain.api.operation.GetPendingAppointmentRequests
import com.bookk.appointments.domain.impl.di.AppointmentsScope
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.application
import io.ktor.server.routing.openapi.describe

fun Routing.requests() {
    authenticate {
        /**
         * Summary: Get pending appointment requests
         * Tag: appointment
         * Security: jwt
         */
        get<Api.Appointment.Requests> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getRequests by application.injectScoped<GetPendingAppointmentRequests>(AppointmentsScope)

            call.respondWith(getRequests(principal.userId, it.businessId))
        }.describe {
            responses {
               response(HttpStatusCode.OK.value) {
                   schema = jsonSchema<List<AppointmentRequest>>()
                   description = "List of appointment requests"
                   ContentType.Application.ProtoBuf()
               }
               response(HttpStatusCode.Forbidden.value) {
                   schema = jsonSchema<SimpleServerError>()
                   description = "Caller is a suspended employee of this business - BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034)"
                   ContentType.Application.ProtoBuf()
               }
            }
        }

        /**
         * Summary: Get own pending appointment requests
         * Description: Get pending appointment requests the caller made in the business
         * Tag: appointment
         * Security: jwt
         */
        get<Api.Appointment.Requests.Own> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getRequests by application.injectScoped<GetAppointmentRequests>(AppointmentsScope)

            call.respondWith(getRequests(principal.userId, it.parent.businessId))
        }.describe {
            responses {
                response(HttpStatusCode.OK.value) {
                    schema = jsonSchema<List<AppointmentRequest>>()
                    description = "List of the caller's pending appointment requests"
                    ContentType.Application.ProtoBuf()
                }
            }
        }

        /**
         * Summary: Create appointment request
         * Description: Create new appointment request
         * Tag: appointment
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.appointments.domain.api.entity.AppointmentRequestDraft]
         * Response: 204 application/x-protobuf Request successfully created
         * Response: 400 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create appointment request errors<br>PRICE_CHANGED (300013) While booking, price for services has been changed, refresh<br>DURATION_CHANGED (300017) While booking, duration for services has been changed, refresh<br>SERVICES_VALIDATION_FAILED (300014) Quote token invalid or requested services does not match with the quote token
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create appointment request errors<br>BUSINESS_EMPLOYEE_NOT_EXISTS (200024) Employee with this id is missing
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create appointment request errors<br>REQUEST_EXISTS (300001) Request for this time already exists<br>DATE_NOT_ALLOWED (300003) Request for this date not allowed<br>TIME_NOT_ALLOWED (300002) Request for this time not allowed<br>DATE_IN_PAST (300012) Request date is in the past<br>QUOTE_TOKEN_ALREADY_USED (300016) Quote token invalid or requested services does not match with the quote token<br>BUSINESS_QUOTE_SERVICE_NOT_FOUND (200013) One or more services not found<br>BUSINESS_EMPLOYEE_SUSPENDED (200033) Employee is suspended and cannot be booked
         * See: docs/operations/appointments/create-appointment-request.md
         */
        post<Api.Appointment.Request> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<AppointmentRequestDraft>()
            val createRequest by application.injectScoped<CreateAppointmentRequest>(AppointmentsScope)

            call.respondWith(
                createRequest(
                    userId = principal.userId,
                    draft = body
                )
            )
        }

        /**
         * Summary: Cancel appointment request
         * Description: Cancel appointment request with specific reason
         * Tag: appointment
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.appointments.domain.api.entity.AppointmentCancellation]
         * Response: 204 application/x-protobuf Appointment request canceled
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Cancel appointment request errors<br>REQUEST_ALREADY_DECLINED (300007) Appointment request already declined<br>REQUEST_ALREADY_APPROVED (300008) Appointment request already approved
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/appointments/decline-appointment-request.md
         */
        post<Api.Appointment.RequestCancel> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<AppointmentCancellation>()
            if (it.id != body.id) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
            } else {
                val cancelRequest by application.injectScoped<DeclineAppointmentRequest>(AppointmentsScope)

                call.respondWith(
                    cancelRequest(
                        userId = principal.userId,
                        cancellation = body
                    )
                )
            }
        }
    }
}
