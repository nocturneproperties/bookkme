package com.bookk.appointments.domain.api.entity

import com.bookk.core.domain.entity.PaginationMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class AppointmentPagination(
    @ProtoNumber(1) val data: List<Appointment>,
    @ProtoNumber(2) val metadata: PaginationMetadata
)