package com.bookk.business.domain.api.employee.entity

import com.bookk.business.domain.api.service.entity.Service
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import library.schedule.Schedule
import kotlin.uuid.Uuid

@Serializable
data class BookableEmployee(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val businessId: Uuid,
    @ProtoNumber(3) val name: String,
    @ProtoNumber(4) val lastName: String,
    @ProtoNumber(5) val services: List<Service>,
    @ProtoNumber(6) val schedule: Schedule,
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            businessId: Uuid = Uuid.random(),
            name: String = "stub-name",
            lastName: String = "stub-lastname",
            services: List<Service> = emptyList(),
            schedule: Schedule = Schedule.empty(),
        ): BookableEmployee {
            return BookableEmployee(
                id = id,
                businessId = businessId,
                name = name,
                lastName = lastName,
                services = services,
                schedule = schedule,
            )
        }
    }
}
