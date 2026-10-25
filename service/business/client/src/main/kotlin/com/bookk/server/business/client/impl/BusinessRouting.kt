package com.bookk.server.business.client.impl

import com.bookk.business.domain.api.business.entity.BusinessResource
import io.ktor.resources.Resource
import kotlin.uuid.Uuid

object BusinessRouting {
    @Resource("api")
    class Api {
        @Resource("/internal")
        class Internal(val parent: Api = Api()) {
            @Resource("/business")
            class Business(val parent: Internal = Internal()) {
                @Resource("/{id}")
                class Id(val parent: Business = Business(), val id: Uuid) {
                    @Resource("/permissions/{userId}/{resource}")
                    class Permissions(val parent: Id, val userId: Uuid, val resource: BusinessResource)

                    @Resource("/appointment-booking-context")
                    class AppointmentBookingContext(val parent: Id)

                    @Resource("/appointment-reschedule-context")
                    class AppointmentRescheduleContext(val parent: Id)

                    @Resource("/services")
                    class Services(val parent: Id)
                }
            }

            @Resource("/client")
            class Client(val parent: Internal = Internal()) {
                @Resource("/{userId}/businesses")
                class Businesses(val parent: Client = Client(), val userId: Uuid)
            }
        }
    }
}
