package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.AppointmentCancellation
import com.bookk.appointments.domain.api.entity.AppointmentUpdate
import com.bookk.appointments.domain.api.entity.InstantAppointmentDraft
import com.bookk.appointments.domain.api.entity.PriceAdjustmentDraft
import com.bookk.appointments.domain.api.operation.CancelAppointment
import com.bookk.appointments.domain.api.operation.CompleteAppointment
import com.bookk.appointments.domain.api.operation.CreateAppointment
import com.bookk.appointments.domain.api.operation.CreateInstantAppointment
import com.bookk.appointments.domain.api.operation.GetAppointmentHistory
import com.bookk.appointments.domain.api.operation.GetAppointmentsForDate
import com.bookk.appointments.domain.api.operation.MarkAppointmentNoShow
import com.bookk.appointments.domain.api.operation.UpdateAppointment
import com.bookk.appointments.domain.impl.di.AppointmentsScope
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.application
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
internal class AppointmentRequestId(
    @ProtoNumber(1) val requestId: Uuid,
)

@Serializable
internal class CompleteAppointmentRequest(
    @ProtoNumber(1) val priceAdjustment: PriceAdjustmentDraft?,
)

fun Routing.appointment() {
    authenticate {
        /**
         * Update appointment
         *
         * Summary: Update appointment
         * Description: Edit a scheduled, completed or no-show appointment within its 7-day edit window - date, note, assigned employee and services. Changing the date or employee reopens it as scheduled and clears the previous checkout adjustment. Other edits preserve the recorded outcome and checkout. Cancelled appointments are terminal and require a new booking. Unresolved appointments cannot be edited. The body carries only ids, the employee and any newly added service are resolved from the business service, a service already on the appointment keeps its stored price. The client and business always stay those of the stored appointment
         * Tag: appointment
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.appointments.domain.api.entity.AppointmentUpdate]
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.Appointment] Updated appointment entity
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Update appointment errors<br>APPOINTMENT_EXISTS (300004) Appointment for this time already exists<br>DATE_NOT_ALLOWED (300003) Request for this date not allowed<br>TIME_NOT_ALLOWED (300002) Request for this time not allowed<br>DATE_IN_PAST (300012) Appointment date is in the past<br>ALREADY_CANCELLED (300005) Appointment already cancelled<br>SERVICE_SELECTION_INVALID (300024) Services must be a non-empty list of distinct services with positive counts<br>BUSINESS_QUOTE_SERVICE_NOT_FOUND (200013) One or more services not found<br>BUSINESS_EMPLOYEE_SUSPENDED (200033) Employee is suspended and cannot be booked<br>APPOINTMENT_EDIT_WINDOW_CLOSED (300026) Appointment ended more than 7 days ago and can no longer be changed
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Assigned employee not found<br>BUSINESS_EMPLOYEE_NOT_EXISTS (200024) Employee with this id is missing
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * Response: 409 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] The appointment changed while it was being rescheduled<br>TRANSACTION_CONFLICT (900001) The request conflicted with concurrent changes, retry it
         * See: docs/operations/appointments/update-appointment.md
         */
        put<Api.Appointment.Id> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<AppointmentUpdate>()
            if (it.id != body.id) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
            } else {
                val updateAppointment by application.injectScoped<UpdateAppointment>(AppointmentsScope)

                call.respondWith(
                    updateAppointment(
                        userId = principal.userId,
                        update = body,
                    ),
                )
            }
        }
        /**
         * Get appointments for specific date
         *
         * Summary: Get appointments for specific date
         * Description: Get appointments of a business for a date, optionally only those assigned to the employee with the given employeeId
         * Tag: appointment
         * Security: jwt
         * Response: 200 application/protobuf [com.bookk.appointments.domain.api.entity.Appointment]+ List of appointments
         * Response: 403 application/protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business - BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034)
         */
        get<Api.Appointments> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getAppointments by application.injectScoped<GetAppointmentsForDate>(AppointmentsScope)

            call.respondWith(getAppointments(principal.userId, it.businessId, it.date, it.employeeId))
        }

        /**
         * Get appointments history
         *
         * Summary: Get appointments history
         * Description: Get paginated appointments history, optionally filtered by a query matching client name or service names
         * Tag: appointment
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.AppointmentPagination] List of appointments
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         */
        get<Api.AppointmentHistory> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getHistory by application.injectScoped<GetAppointmentHistory>(AppointmentsScope)

            call.respondWith(
                getHistory(
                    userId = principal.userId,
                    businessId = it.businessId,
                    limit = it.limit,
                    offset = it.offset,
                    query = it.query,
                ),
            )
        }

        /**
         * Create appointment
         *
         * Summary: Create appointment
         * Description: Create new appointment from request
         * Tag: appointment
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.appointments.microservice.route.api.AppointmentRequestId]
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.Appointment] Created appointment entity
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create appointment errors<br>APPOINTMENT_EXISTS (300004) Appointment for this time already exists<br>DATE_NOT_ALLOWED (300003) Request for this date not allowed<br>TIME_NOT_ALLOWED (300002) Request for this time not allowed<br>DATE_IN_PAST (300012) Appointment date is in the past<br>REQUEST_ALREADY_DECLINED (300007) Appointment request already declined<br>REQUEST_ALREADY_APPROVED (300008) Appointment request already approved
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/appointments/create-appointment-from-request.md
         */
        post<Api.Appointment> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<AppointmentRequestId>()
            val createAppointment by application.injectScoped<CreateAppointment>(AppointmentsScope)

            call.respondWith(
                createAppointment(
                    userId = principal.userId,
                    appointmentRequestId = body.requestId,
                ),
            )
        }

        /**
         * Create instant appointment
         *
         * Summary: Create instant appointment
         * Description: An employee books a client of the business straight into a schedule, without a request. A caller with the appointments update grant may book any employee of the business, a caller with only the view grant only themselves. The body names the business, the employee, an existing client of the business, the services with counts, the date, a note and the quote token for those services. The employee, client and services are resolved from the business, and the quote token is spent once
         * Tag: appointment
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.appointments.domain.api.entity.InstantAppointmentDraft]
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.Appointment] Created appointment entity
         * Response: 400 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create instant appointment errors<br>PRICE_CHANGED (300013) While booking, price for services has been changed, refresh<br>DURATION_CHANGED (300017) While booking, duration for services has been changed, refresh<br>SERVICES_VALIDATION_FAILED (300014) Quote token invalid or requested services does not match with the quote token
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create instant appointment errors<br>BUSINESS_EMPLOYEE_NOT_EXISTS (200024) Employee with this id is missing<br>BUSINESS_CLIENT_NOT_EXISTS (200006) Client not found<br>Also returned when the caller may not book into that employee's schedule, business settings are missing, or the client or their account was deleted
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create instant appointment errors<br>APPOINTMENT_EXISTS (300004) Appointment for this time already exists<br>DATE_NOT_ALLOWED (300003) Request for this date and time not allowed<br>TIME_NOT_ALLOWED (300002) Request for this time not allowed<br>DATE_IN_PAST (300012) Appointment date is in the past<br>QUOTE_TOKEN_ALREADY_USED (300016) Quote token invalid or requested services does not match with the quote token<br>BUSINESS_QUOTE_SERVICE_NOT_FOUND (200013) One or more services not found<br>BUSINESS_QUOTE_EMPTY_SERVICE_LIST (200014) Service list must not be empty<br>BUSINESS_EMPLOYEE_SUSPENDED (200033) Employee is suspended and cannot be booked
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/appointments/create-appointment-instant.md
         */
        post<Api.Appointment.Instant> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<InstantAppointmentDraft>()
            val createInstantAppointment by application.injectScoped<CreateInstantAppointment>(AppointmentsScope)

            call.respondWith(createInstantAppointment(userId = principal.userId, draft = body))
        }

        /**
         * Cancel appointment
         *
         * Summary: Cancel appointment
         * Description: Cancel a scheduled, completed or no-show appointment within its 7-day edit window with a specific reason. Cancellation is terminal and requires a new booking to correct. Unresolved appointments cannot be cancelled
         * Tag: appointment
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.appointments.domain.api.entity.AppointmentCancellation]
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.Appointment] Canceled appointment
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Cancel appointment errors<br>ALREADY_CANCELLED (300005) Appointment already canceled<br>APPOINTMENT_EDIT_WINDOW_CLOSED (300026) Appointment ended more than 7 days ago and can no longer be changed
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/appointments/cancel-appointment.md
         */
        post<Api.Appointment.Cancel> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<AppointmentCancellation>()
            if (it.id != body.id) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
            } else {
                val cancelAppointment by application.injectScoped<CancelAppointment>(AppointmentsScope)

                call.respondWith(
                    cancelAppointment(
                        userId = principal.userId,
                        cancellation = body,
                    ),
                )
            }
        }

        /**
         * Mark appointment as no-show
         *
         * Summary: Mark appointment as no-show
         * Description: Mark a started scheduled or completed appointment as missed by the client
         * Tag: appointment
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.Appointment] Appointment marked as no-show
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Mark appointment as no-show errors<br>ALREADY_CANCELLED (300005) Appointment already cancelled<br>NOT_STARTED (300018) Appointment has not started yet<br>APPOINTMENT_EDIT_WINDOW_CLOSED (300026) Appointment ended more than 7 days ago and can no longer be changed
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/appointments/mark-appointment-no-show.md
         */
        post<Api.Appointment.NoShow> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val markAppointmentNoShow by application.injectScoped<MarkAppointmentNoShow>(AppointmentsScope)

            call.respondWith(markAppointmentNoShow(userId = principal.userId, appointmentId = it.id))
        }

        /**
         * Complete appointment
         *
         * Summary: Complete appointment
         * Description: Mark a started scheduled appointment as completed by the caller. An already completed appointment keeps its status and completer. The optional body attaches a price adjustment (final charged price, additional catalog services resolved by id, optional reason) to the completed appointment, replacing any previous one
         * Tag: appointment
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.appointments.microservice.route.api.CompleteAppointmentRequest]
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.Appointment] Completed appointment
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Complete appointment errors<br>ALREADY_CANCELLED (300005) Appointment already cancelled<br>NOT_STARTED (300018) Appointment has not started yet<br>PRICE_ADJUSTMENT_NEGATIVE_PRICE (300021) Adjusted price must not be negative<br>PRICE_ADJUSTMENT_CURRENCY_MISMATCH (300022) Adjusted price currency must match the appointment currency<br>PRICE_ADJUSTMENT_REASON_TOO_LONG (300023) Price adjustment reason is too long<br>BUSINESS_QUOTE_SERVICE_NOT_FOUND (200013) One or more additional services not found<br>APPOINTMENT_EDIT_WINDOW_CLOSED (300026) Appointment ended more than 7 days ago and can no longer be changed
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/appointments/complete-appointment.md
         */
        post<Api.Appointment.Complete> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receiveNullable<CompleteAppointmentRequest>()
            val completeAppointment by application.injectScoped<CompleteAppointment>(AppointmentsScope)

            call.respondWith(completeAppointment(userId = principal.userId, appointmentId = it.id, priceAdjustment = body?.priceAdjustment))
        }
    }
}
