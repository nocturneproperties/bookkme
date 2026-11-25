package com.bookk.appointments.microservice.route

import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.BookingPeriod
import com.bookk.appointments.domain.api.entity.HistoryOrder
import io.ktor.resources.Resource
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

object AppointmentsRouting {
    @Resource("api")
    class Api {
        @Resource("/internal")
        class Internal(val parent: Api = Api()) {
            @Resource("/export")
            class Export(val parent: Internal = Internal()) {
                @Resource("/appointments/{userId}")
                class User(val parent: Export = Export(), val userId: Uuid)

                @Resource("/appointments/business/{businessId}")
                class Business(val parent: Export = Export(), val businessId: Uuid)
            }
        }

        @Resource("/appointments")
        class Appointment(val parent: Api = Api()) {

            @Resource("/instant")
            class Instant(val parent: Appointment = Appointment())

            @Resource("/enabled/{businessId}")
            class Enabled(val parent: Appointment = Appointment(), val businessId: Uuid)

            @Resource("/upcoming")
            class Upcoming(val parent: Appointment = Appointment())

            @Resource("/mine")
            class OwnHistory(
                val parent: Appointment = Appointment(),
                val limit: Int? = null,
                val cursor: String? = null,
                val period: BookingPeriod? = null,
                val status: List<AppointmentStatus> = emptyList(),
            )

            @Resource("/request/mine")
            class OwnRequestHistory(
                val parent: Appointment = Appointment(),
                val limit: Int? = null,
                val cursor: String? = null,
                val period: BookingPeriod? = null,
                val status: List<AppointmentRequestStatus> = emptyList(),
            )

            @Resource("/businesses")
            class SavedBusinesses(val parent: Appointment = Appointment())

            @Resource("/businesses/{businessId}")
            class SavedBusiness(val parent: Appointment = Appointment(), val businessId: Uuid)

            @Resource("/slots")
            class Slots(val parent: Appointment = Appointment())

            @Resource("/booking-info/{businessId}")
            class BookingInfo(val parent: Appointment = Appointment(), val businessId: Uuid)

            @Resource("/{id}")
            class Id(val parent: Appointment = Appointment(), val id: Uuid)

            @Resource("/{id}/cancel")
            class Cancel(val parent: Appointment = Appointment(), val id: Uuid)

            @Resource("/{id}/no-show")
            class NoShow(val parent: Appointment = Appointment(), val id: Uuid)

            @Resource("/{id}/complete")
            class Complete(val parent: Appointment = Appointment(), val id: Uuid)

            @Resource("/healthcheck")
            class HealthCheck(val parent: Appointment = Appointment())

            @Resource("/request")
            class Request(val parent: Appointment = Appointment())

            @Resource("/request/{id}/decline")
            class RequestCancel(val parent: Appointment = Appointment(), val id: Uuid)

            @Resource("/request/{id}/withdraw")
            class RequestWithdraw(val parent: Appointment = Appointment(), val id: Uuid)

            @Resource("/request/history/{businessId}")
            class RequestHistory(
                val parent: Appointment = Appointment(),
                val businessId: Uuid,
                val limit: Int? = null,
                val cursor: String? = null,
                val clientId: Uuid? = null,
                val employeeId: Uuid? = null,
                val status: List<AppointmentRequestStatus> = emptyList(),
                val from: LocalDate? = null,
                val to: LocalDate? = null,
                val order: HistoryOrder? = null,
            )

            @Resource("/request/{businessId}/mine")
            class OwnRequests(val parent: Appointment = Appointment(), val businessId: Uuid)

            @Resource("/settings/{businessId}")
            class Settings(val parent: Appointment = Appointment(), val businessId: Uuid)
        }

        @Resource("/appointments/list/{businessId}")
        class Appointments(
            val parent: Api = Api(),
            val businessId: Uuid,
            val date: LocalDate,
            val employeeId: Uuid? = null,
        )

        @Resource("/appointments/history/{businessId}")
        class AppointmentHistory(
            val parent: Api = Api(),
            val businessId: Uuid,
            val limit: Int? = null,
            val cursor: String? = null,
            val query: String? = null,
            val clientId: Uuid? = null,
            val employeeId: Uuid? = null,
            val status: List<AppointmentStatus> = emptyList(),
            val from: LocalDate? = null,
            val to: LocalDate? = null,
            val order: HistoryOrder? = null,
        )
    }
}
