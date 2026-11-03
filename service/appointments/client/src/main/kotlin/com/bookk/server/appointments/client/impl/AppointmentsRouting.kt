package com.bookk.server.appointments.client.impl

import io.ktor.resources.Resource
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
            }
        }
    }
}
