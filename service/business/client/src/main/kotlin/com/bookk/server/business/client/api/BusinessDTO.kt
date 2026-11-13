package com.bookk.server.business.client.api

import com.bookk.business.domain.api.business.entity.Business
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import library.schedule.Schedule
import kotlin.uuid.Uuid

@Serializable
data class BusinessDTO(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val address: String,
    @ProtoNumber(4) val timeZone: TimeZone,
    @ProtoNumber(5) val schedule: Schedule,
    @ProtoNumber(6) val ownerUserId: Uuid?,
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            name: String = "Business name",
            address: String = "Business address",
            timeZone: TimeZone = TimeZone.UTC,
            schedule: Schedule = Schedule(),
            ownerUserId: Uuid? = Uuid.random(),
        ): BusinessDTO {
            return BusinessDTO(
                id = id,
                name = name,
                address = address,
                timeZone = timeZone,
                schedule = schedule,
                ownerUserId = ownerUserId,
            )
        }

        fun from(business: Business): BusinessDTO {
            return BusinessDTO(
                id = business.id,
                name = business.name,
                address = business.address,
                timeZone = business.timeZone,
                schedule = business.schedule,
                ownerUserId = business.ownerId,
            )
        }
    }
}
