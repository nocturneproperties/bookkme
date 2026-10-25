package com.bookk.business.domain.api.business.entity

import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import library.schedule.Schedule
import kotlin.uuid.Uuid

@Serializable
data class BusinessUpdateModel(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val name: String?,
    @ProtoNumber(3) val description: String?,
    @ProtoNumber(4) val address: String?,
    @ProtoNumber(5) val location: Business.Location?,
    @ProtoNumber(6) val currencyCode: String?,
    @ProtoNumber(7) val timeZone: TimeZone?,
    @ProtoNumber(8) val socials: List<Business.Social>?,
    @ProtoNumber(9) val schedule: Schedule?
)