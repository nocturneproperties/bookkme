package com.bookk.appointments.microservice.route.api

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
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Routing
import io.ktor.server.routing.application

fun Routing.clientBookings() {
    authenticate {
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
