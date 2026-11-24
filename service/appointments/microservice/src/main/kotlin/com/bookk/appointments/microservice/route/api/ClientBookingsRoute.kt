package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.RequestedService
import com.bookk.appointments.domain.api.operation.GetAvailableSlots
import com.bookk.appointments.domain.api.operation.GetBookingInfo
import com.bookk.appointments.domain.api.operation.GetSavedBusinesses
import com.bookk.appointments.domain.api.operation.GetUpcomingBookings
import com.bookk.appointments.domain.api.operation.RemoveSavedBusiness
import com.bookk.appointments.domain.api.operation.SaveBusiness
import com.bookk.appointments.domain.impl.di.AppointmentsScope
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Routing
import io.ktor.server.routing.application
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
internal class AvailableSlotsRequest(
    @ProtoNumber(1) val businessId: Uuid,
    @ProtoNumber(2) val employeeId: Uuid,
    @ProtoNumber(3) val services: List<RequestedService>,
    @ProtoNumber(4) val from: LocalDate,
    @ProtoNumber(5) val to: LocalDate,
)

fun Routing.clientBookings() {
    authenticate {
        /**
         * Get available slots
         *
         * Summary: Get available slots
         * Description: Free start times for booking the given services with one employee, for every date from from to to (inclusive, in the business time zone, at most 31 days). Starts are on a 15 minute grid, the whole booking fits both the business and the employee schedules, starts in the past are left out, and the employee's scheduled appointments block their time plus the business break before and after (pending requests don't). A date with nothing free has an empty list. Any signed in user may ask
         * Tag: appointment
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.appointments.microservice.route.api.AvailableSlotsRequest]
         * Response: 200 application/protobuf [com.bookk.appointments.domain.api.entity.DaySlots]+ Free slots per date
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] The business does not take appointments<br>BUSINESS_EMPLOYEE_NOT_EXISTS (200024) Employee with this id is missing
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Get available slots errors<br>SERVICE_SELECTION_INVALID (300024) Services must be a non-empty list of distinct services with positive counts, at most 100 in total<br>SLOT_RANGE_INVALID (300033) The date range must not end before it starts and must span at most 31 days<br>BUSINESS_QUOTE_SERVICE_NOT_FOUND (200013) One or more services not found<br>BUSINESS_EMPLOYEE_SUSPENDED (200033) Employee is suspended and cannot be booked
         */
        post<Api.Appointment.Slots> {
            val body = call.receive<AvailableSlotsRequest>()
            val getAvailableSlots by application.injectScoped<GetAvailableSlots>(AppointmentsScope)

            call.respondWith(getAvailableSlots(body.businessId, body.employeeId, body.services, body.from, body.to))
        }

        /**
         * Get booking info
         *
         * Summary: Get booking info
         * Description: What a client needs to know before booking at a business, currently the note the business wrote for clients in its appointment settings (empty when it wrote none). Any signed in user may ask
         * Tag: appointment
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.BookingInfo] Booking info of the business
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] The business does not take appointments
         */
        get<Api.Appointment.BookingInfo> {
            val getBookingInfo by application.injectScoped<GetBookingInfo>(AppointmentsScope)

            call.respondWith(getBookingInfo(it.businessId))
        }

        /**
         * Get upcoming bookings
         *
         * Summary: Get upcoming bookings
         * Description: The caller's 2 nearest scheduled appointments that have not ended yet and 2 nearest pending requests that have not started yet, across every business, soonest first. Appointments are those whose client is the caller's account; the business's own note is left empty and the client's note from the request is in clientNote. Empty lists when the caller has none
         * Tag: appointment
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.appointments.domain.api.entity.UpcomingBookings] The caller's upcoming appointments and requests
         */
        get<Api.Appointment.Upcoming> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getUpcomingBookings by application.injectScoped<GetUpcomingBookings>(AppointmentsScope)

            call.respondWith(getUpcomingBookings(principal.userId))
        }

        /**
         * Get saved businesses
         *
         * Summary: Get saved businesses
         * Description: Businesses the caller saved, most recently saved first, with whether each takes appointments now and the start of the caller's last completed appointment there (absent when they never visited). Read names and details from the business service by id
         * Tag: appointment
         * Security: jwt
         * Response: 200 application/protobuf [com.bookk.appointments.domain.api.entity.SavedBusiness]+ The caller's saved businesses
         */
        get<Api.Appointment.SavedBusinesses> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getSavedBusinesses by application.injectScoped<GetSavedBusinesses>(AppointmentsScope)

            call.respondWith(getSavedBusinesses(principal.userId))
        }

        /**
         * Save business
         *
         * Summary: Save business
         * Description: Adds a business that takes appointments to the caller's saved businesses, for example when the caller opens its link. Saving a business already saved changes nothing
         * Tag: appointment
         * Security: jwt
         * Response: 204 application/x-protobuf Business saved
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] The caller's account was deleted
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Save business errors<br>APPOINTMENTS_NOT_ENABLED (300030) This business does not take appointments<br>SAVED_BUSINESSES_LIMIT_REACHED (300031) You already saved the maximum number of businesses
         * See: docs/operations/appointments/save-business.md
         */
        post<Api.Appointment.SavedBusiness> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val saveBusiness by application.injectScoped<SaveBusiness>(AppointmentsScope)

            call.respondWith(saveBusiness(principal.userId, it.businessId))
        }

        /**
         * Remove saved business
         *
         * Summary: Remove saved business
         * Description: Removes a business from the caller's saved businesses. Removing one that is not saved changes nothing
         * Tag: appointment
         * Security: jwt
         * Response: 204 application/x-protobuf Business removed from the saved businesses
         * See: docs/operations/appointments/remove-saved-business.md
         */
        delete<Api.Appointment.SavedBusiness> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val removeSavedBusiness by application.injectScoped<RemoveSavedBusiness>(AppointmentsScope)

            call.respondWith(removeSavedBusiness(principal.userId, it.businessId))
        }
    }
}
