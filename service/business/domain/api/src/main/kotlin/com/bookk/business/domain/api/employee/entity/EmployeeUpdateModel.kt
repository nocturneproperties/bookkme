package com.bookk.business.domain.api.employee.entity

import com.bookk.business.domain.api.service.entity.Service
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import library.schedule.Schedule
import kotlin.uuid.Uuid

@Serializable
data class EmployeeUpdateModel(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val businessId: Uuid,
    @ProtoNumber(3) val name: String,
    @ProtoNumber(4) val lastName: String,
    @ProtoNumber(5) val phone: String?,
    @ProtoNumber(6) val email: String?,
    @ProtoNumber(8) val services: List<Service>,
    @ProtoNumber(9) val schedule: Schedule
)
