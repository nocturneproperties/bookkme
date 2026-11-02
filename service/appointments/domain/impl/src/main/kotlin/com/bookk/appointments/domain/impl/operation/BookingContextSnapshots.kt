package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.ClientSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.RequestedService
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext

internal fun AppointmentBookingContext.employeeSnapshot() = EmployeeSnapshot(
    id = employee.id,
    userId = employee.userId,
    fullName = "${employee.name} ${employee.lastName}".trim(),
)

internal fun AppointmentBookingContext.clientSnapshot() = ClientSnapshot(
    id = client.id,
    fullName = "${client.name} ${client.lastName}".trim(),
    phone = client.phone,
    email = client.email,
    userId = client.userId,
)

internal fun AppointmentBookingContext.serviceSnapshots(requested: List<RequestedService>): List<ServiceSnapshot> {
    val servicesById = services.associateBy { it.id }
    return requested
        .flatMap { service -> List(service.count) { servicesById.getValue(service.serviceId) } }
        .map { ServiceSnapshot(id = it.id, name = it.name, groupId = it.group.id, price = it.price, duration = it.duration) }
}
