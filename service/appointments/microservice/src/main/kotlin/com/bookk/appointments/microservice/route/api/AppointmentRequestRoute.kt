package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.AppointmentCancellation
import com.bookk.appointments.domain.api.entity.AppointmentRequestDraft
import com.bookk.appointments.domain.api.entity.AppointmentRequestHistoryFilter
import com.bookk.appointments.domain.api.entity.HistoryDates
import com.bookk.appointments.domain.api.entity.HistoryOrder
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest
import com.bookk.appointments.domain.api.operation.DeclineAppointmentRequest
import com.bookk.appointments.domain.api.operation.GetAppointmentRequestHistory
import com.bookk.appointments.domain.api.operation.GetAppointmentRequests
import com.bookk.appointments.domain.api.operation.WithdrawAppointmentRequest
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
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.application

fun Routing.requests() {
    authenticate {
        /**
         * Get appointment request history
         *
         * Summary: Get appointment request history
         * Description: Get one page of the business's appointment requests in any status. Filter by clientId (the business's client id), employeeId, one or more status (repeat the parameter), and from and to (inclusive dates in the business time zone, compared with the request's start). Pass order NEWEST_FIRST (default) or SOONEST_FIRST, limit (1 to 100, default 50) and, for the next page, the previous page's nextCursor as cursor with the same filters; the last page has no nextCursor. An employee lists the requests assigned to them with their own employeeId, status PENDING and order SOONEST_FIRST
         * Tag: appointment
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.AppointmentRequestPage] Page of appointment requests
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Request history errors<br>PAGE_LIMIT_INVALID (900003) Page limit must be from 1 to 100<br>PAGE_CURSOR_INVALID (900004) Page cursor is invalid, start from the first page<br>HISTORY_DATE_RANGE_INVALID (300034) The last date is before the first date
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller has no appointments view permission in this business, or dates were given for a business that doesn't take appointments
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         */
        get<Api.Appointment.RequestHistory> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getHistory by application.injectScoped<GetAppointmentRequestHistory>(AppointmentsScope)
            val filter = AppointmentRequestHistoryFilter(
                clientId = it.clientId,
                employeeId = it.employeeId,
                statuses = it.status.toSet(),
                dates = HistoryDates(from = it.from, to = it.to),
                order = it.order ?: HistoryOrder.NEWEST_FIRST,
            )

            call.respondWith(getHistory(principal.userId, it.businessId, filter, it.limit, it.cursor))
        }

        /**
         * Get own pending appointment requests
         *
         * Summary: Get own pending appointment requests
         * Description: Get pending appointment requests the caller made in the business
         * Tag: appointment
         * Security: jwt
         * Response: 200 application/protobuf [com.bookk.appointments.domain.api.entity.AppointmentRequest]+ List of the caller's pending appointment requests
         */
        get<Api.Appointment.OwnRequests> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getRequests by application.injectScoped<GetAppointmentRequests>(AppointmentsScope)

            call.respondWith(getRequests(principal.userId, it.businessId))
        }

        /**
         * Create appointment request
         *
         * Summary: Create appointment request
         * Description: Create new appointment request. The slot must fit both the business schedule and the employee's schedule, where a working range whose end is at or before its start runs past midnight. A demo account can't make appointment requests anywhere, its own demo business included, until it is upgraded
         * Tag: appointment
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.appointments.domain.api.entity.AppointmentRequestDraft]
         * Response: 204 application/x-protobuf Request successfully created
         * Response: 400 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create appointment request errors<br>PRICE_CHANGED (300013) While booking, price for services has been changed, refresh<br>DURATION_CHANGED (300017) While booking, duration for services has been changed, refresh<br>SERVICES_VALIDATION_FAILED (300014) Quote token invalid or requested services does not match with the quote token
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create appointment request errors<br>BUSINESS_EMPLOYEE_NOT_EXISTS (200024) Employee with this id is missing<br>Also returned when the account booking was deleted
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create appointment request errors<br>BUSINESS_DATE_NOT_ALLOWED (300003) The business does not work on this date<br>BUSINESS_TIME_NOT_ALLOWED (300002) This time is outside the business working hours<br>EMPLOYEE_DATE_NOT_ALLOWED (300027) The employee does not work on this date<br>EMPLOYEE_TIME_NOT_ALLOWED (300028) This time is outside the employee working hours<br>DATE_IN_PAST (300012) Request date is in the past<br>QUOTE_TOKEN_ALREADY_USED (300016) Quote token invalid or requested services does not match with the quote token<br>BUSINESS_QUOTE_SERVICE_NOT_FOUND (200013) One or more services not found<br>BUSINESS_EMPLOYEE_SUSPENDED (200033) Employee is suspended and cannot be booked<br>PENDING_REQUESTS_LIMIT_REACHED (300029) You already have the maximum number of pending requests at this business<br>EMPLOYEE_BUSY (300032) The employee already has a booking at this time<br>APPOINTMENTS_DEMO_RESTRICTED (300035) A demo account can't book appointments, upgrade the account first
         * See: docs/operations/appointments/create-appointment-request.md
         */
        post<Api.Appointment.Request> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<AppointmentRequestDraft>()
            val createRequest by application.injectScoped<CreateAppointmentRequest>(AppointmentsScope)

            call.respondWith(
                createRequest(
                    userId = principal.userId,
                    draft = body,
                    isDemo = principal.isDemo,
                ),
            )
        }

        /**
         * Cancel appointment request
         *
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
                        cancellation = body,
                    ),
                )
            }
        }

        /**
         * Withdraw appointment request
         *
         * Summary: Withdraw appointment request
         * Description: Withdraws a pending appointment request the caller made, freeing its place under the business's limit of pending requests per client. The business is not notified; the request disappears from its pending list
         * Tag: appointment
         * Security: jwt
         * Response: 204 application/x-protobuf Appointment request withdrawn
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Request is not found or was made by someone else
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Withdraw appointment request errors<br>REQUEST_ALREADY_APPROVED (300008) Appointment request already approved<br>REQUEST_ALREADY_DECLINED (300007) Appointment request already declined
         * See: docs/operations/appointments/withdraw-appointment-request.md
         */
        post<Api.Appointment.RequestWithdraw> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val withdrawRequest by application.injectScoped<WithdrawAppointmentRequest>(AppointmentsScope)

            call.respondWith(withdrawRequest(principal.userId, it.id))
        }
    }
}
