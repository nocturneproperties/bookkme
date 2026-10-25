package com.bookk.appointments.microservice.route

import io.ktor.resources.Resource
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

object AppointmentsRouting {
    @Resource("api")
    class Api {

        @Resource("/appointments")
        class Appointment(val parent: Api = Api()) {

            @Resource("/instant")
            class Instant(val parent: Appointment = Appointment())

            @Resource("/enabled/{businessId}")
            class Enabled(val parent: Appointment = Appointment(), val businessId: Uuid)

            @Resource("/enabled")
            class EnabledForClientBusinesses(val parent: Appointment = Appointment())

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

            @Resource("/request/{businessId}")
            class Requests(val parent: Appointment = Appointment(), val businessId: Uuid) {

                @Resource("/mine")
                class Own(val parent: Requests)
            }

            @Resource("/settings/{businessId}")
            class Settings(val parent: Appointment = Appointment(), val businessId: Uuid)
        }

        @Resource("/appointments/list/{businessId}")
        class Appointments(
            val parent: Api = Api(),
            val businessId: Uuid,
            val date: LocalDate,
            val employeeId: Uuid? = null
        )

        @Resource("/appointments/history/{businessId}")
        class AppointmentHistory(
            val parent: Api = Api(),
            val businessId: Uuid,
            val limit: Int,
            val offset: Long,
            val query: String? = null
        )
    }

}