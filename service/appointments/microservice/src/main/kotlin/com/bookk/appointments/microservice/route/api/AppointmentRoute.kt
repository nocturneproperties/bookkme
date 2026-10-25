package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentCancellation
import com.bookk.appointments.domain.api.entity.AppointmentUpdate
import com.bookk.appointments.domain.api.entity.PriceAdjustmentDraft
import com.bookk.appointments.domain.api.operation.CancelAppointment
import com.bookk.appointments.domain.api.operation.CompleteAppointment
import com.bookk.appointments.domain.api.operation.CreateAppointment
import com.bookk.appointments.domain.api.operation.GetAppointmentHistory
import com.bookk.appointments.domain.api.operation.GetAppointmentsForDate
import com.bookk.appointments.domain.api.operation.MarkAppointmentNoShow
import com.bookk.appointments.domain.api.operation.UpdateAppointment
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
import io.ktor.server.request.receiveNullable
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.application
import io.ktor.server.routing.openapi.describe
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
internal class AppointmentRequestId(
    @ProtoNumber(1) val requestId: Uuid,
)

@Serializable
internal class CompleteAppointmentRequest(
    @ProtoNumber(1) val priceAdjustment: PriceAdjustmentDraft?
)

fun Routing.appointment() {
    authenticate {
        /**
         * Summary: Update appointment
         * Description: Reschedule a scheduled appointment - date, note, assigned employee and services. The body carries only ids, the employee and any newly added service are resolved from the business service, a service already on the appointment keeps its stored price. The client and business always stay those of the stored appointment
         * Tag: appointment
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.appointments.domain.api.entity.AppointmentUpdate]
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.Appointment] Updated appointment entity
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Update appointment errors<br>APPOINTMENT_EXISTS (300004) Appointment for this time already exists<br>DATE_NOT_ALLOWED (300003) Request for this date not allowed<br>TIME_NOT_ALLOWED (300002) Request for this time not allowed<br>DATE_IN_PAST (300012) Appointment date is in the past<br>ALREADY_CANCELLED (300005) Appointment already cancelled<br>ALREADY_COMPLETED (300006) Appointment already completed<br>MARKED_NO_SHOW (300019) Appointment is marked as no-show<br>SERVICE_SELECTION_INVALID (300024) Services must be a non-empty list of distinct services with positive counts<br>BUSINESS_QUOTE_SERVICE_NOT_FOUND (200013) One or more services not found<br>BUSINESS_EMPLOYEE_SUSPENDED (200033) Employee is suspended and cannot be booked
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Assigned employee not found<br>BUSINESS_EMPLOYEE_NOT_EXISTS (200024) Employee with this id is missing
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
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
                        update = body
                    )
                )
            }
        }
        /**
         * Summary: Get appointments for specific date
         * Description: Get appointments of a business for a date, optionally only those assigned to the employee with the given employeeId
         * Tag: appointment
         * Security: jwt
         */
        get<Api.Appointments> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getAppointments by application.injectScoped<GetAppointmentsForDate>(AppointmentsScope)

            call.respondWith(getAppointments(principal.userId, it.businessId, it.date, it.employeeId))
        }.describe {
            responses {
                response(HttpStatusCode.OK.value) {
                    schema = jsonSchema<List<Appointment>>()
                    description = "List of appointments"
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
                    query = it.query
                )
            )
        }

        /**
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
                    appointmentRequestId = body.requestId
                )
            )
        }

        /**
         * Summary: Create appointment
         * Description: Create new appointment from request
         * Tag: appointment
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.appointments.domain.api.entity.Appointment]
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.Appointment] Created appointment entity
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create appointment errors<br>APPOINTMENT_EXISTS (300004) Appointment for this time already exists<br>DATE_NOT_ALLOWED (300003) Request for this date not allowed<br>TIME_NOT_ALLOWED (300002) Request for this time not allowed<br>DATE_IN_PAST (300012) Appointment date is in the past
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/appointments/create-appointment-instant.md
         */
        post<Api.Appointment.Instant> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<Appointment>()
            val createAppointment by application.injectScoped<CreateAppointment>(AppointmentsScope)

            call.respondWith(
                createAppointment(
                    userId = principal.userId,
                    appointment = body,
                    isInstant = true
                )
            )
        }

        /**
         * Summary: Cancel appointment
         * Description: Cancel appointment with specific reason
         * Tag: appointment
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.appointments.domain.api.entity.AppointmentCancellation]
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.Appointment] Canceled appointment
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Cancel appointment errors<br>ALREADY_CANCELLED (300005) Appointment already canceled<br>ALREADY_COMPLETED (300006) Appointment already completed<br>MARKED_NO_SHOW (300019) Appointment is marked as no-show
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
                        cancellation = body
                    )
                )
            }
        }

        /**
         * Summary: Mark appointment as no-show
         * Description: Mark a started scheduled or completed appointment as missed by the client
         * Tag: appointment
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.Appointment] Appointment marked as no-show
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Mark appointment as no-show errors<br>ALREADY_CANCELLED (300005) Appointment already cancelled<br>NOT_STARTED (300018) Appointment has not started yet
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/appointments/mark-appointment-no-show.md
         */
        post<Api.Appointment.NoShow> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val markAppointmentNoShow by application.injectScoped<MarkAppointmentNoShow>(AppointmentsScope)

            call.respondWith(markAppointmentNoShow(userId = principal.userId, appointmentId = it.id))
        }

        /**
         * Summary: Complete appointment
         * Description: Mark a started scheduled appointment as completed by the caller. An already completed appointment keeps its status and completer. The optional body attaches a price adjustment (final charged price, additional catalog services resolved by id, optional reason) to the completed appointment, replacing any previous one
         * Tag: appointment
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.appointments.microservice.route.api.CompleteAppointmentRequest]
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.Appointment] Completed appointment
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Complete appointment errors<br>ALREADY_CANCELLED (300005) Appointment already cancelled<br>NOT_STARTED (300018) Appointment has not started yet<br>MARKED_NO_SHOW (300019) Appointment is marked as no-show<br>PRICE_ADJUSTMENT_NEGATIVE_PRICE (300021) Adjusted price must not be negative<br>PRICE_ADJUSTMENT_CURRENCY_MISMATCH (300022) Adjusted price currency must match the appointment currency<br>PRICE_ADJUSTMENT_REASON_TOO_LONG (300023) Price adjustment reason is too long<br>BUSINESS_QUOTE_SERVICE_NOT_FOUND (200013) One or more additional services not found
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
