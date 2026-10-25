package com.bookk.business.domain.api.appointment.entity

import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.service.entity.Service
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class AppointmentRescheduleContext(
    @ProtoNumber(1) val employee: Employee?,
    @ProtoNumber(2) val services: List<Service>
) {
    companion object {
        fun stub(
            employee: Employee? = Employee.stub(),
            services: List<Service> = listOf(Service.stub())
        ) = AppointmentRescheduleContext(employee = employee, services = services)
    }
}
