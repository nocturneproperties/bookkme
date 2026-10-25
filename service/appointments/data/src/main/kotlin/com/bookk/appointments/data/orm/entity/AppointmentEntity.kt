package com.bookk.appointments.data.orm.entity

import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.AppointmentServicesTable
import com.bookk.appointments.data.orm.table.AppointmentTable
import com.bookk.appointments.data.orm.table.PriceAdjustmentTable
import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.ClientSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.PriceAdjustment
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.core.data.DecoratorUuidEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class AppointmentEntity(id: EntityID<Uuid>) : UuidEntity(id) {

    var userId by AppointmentTable.userId
    var businessId by AppointmentTable.businessId
    var employeeId by AppointmentTable.employeeId
    var employeeUserId by AppointmentTable.employeeUserId
    var employeeName by AppointmentTable.employeeName
    var clientId by AppointmentTable.clientId
    var clientName by AppointmentTable.clientName
    var clientPhone by AppointmentTable.clientPhone
    var clientEmail by AppointmentTable.clientEmail
    val services by AppointmentServiceEntity referrersOn AppointmentServicesTable.appointmentId
    val priceAdjustments by PriceAdjustmentEntity referrersOn PriceAdjustmentTable.appointmentId
    var dateStart by AppointmentTable.dateStart
    var dateEnd by AppointmentTable.dateEnd
    var note by AppointmentTable.note
    var status by AppointmentTable.status
    var cancellationReason by AppointmentTable.cancellationReason
    var completedBy by AppointmentTable.completedBy
    var updatedAt by AppointmentTable.updatedAt

    fun domain(): Appointment {
        return Appointment(
            id = id.value,
            userId = userId,
            businessId = businessId.value,
            employee = EmployeeSnapshot(
                id = employeeId,
                userId = employeeUserId,
                fullName = employeeName
            ),
            client = ClientSnapshot(
                id = clientId,
                fullName = clientName,
                phone = clientPhone,
                email = clientEmail
            ),
            services = services.map {
                ServiceSnapshot(
                    id = it.serviceId,
                    name = it.serviceName,
                    groupId = it.serviceGroupId,
                    price = Money.of(
                        CurrencyUnit.of(it.priceCurrency),
                        BigDecimal(BigInteger.valueOf(it.priceUnscaled), it.priceScale)
                    ),
                    duration = it.durationMinutes.minutes
                )
            },
            date = dateStart,
            note = note,
            status = status,
            cancellationReason = cancellationReason,
            completedBy = completedBy,
            priceAdjustment = priceAdjustments.singleOrNull()?.domain()
        )
    }

    fun replacePriceAdjustment(adjustment: PriceAdjustment) {
        val entityId = id
        PriceAdjustmentTable.deleteWhere { PriceAdjustmentTable.appointmentId eq entityId }
        PriceAdjustmentEntity.new(entityId, adjustment)
    }

    companion object : DecoratorUuidEntityClass<AppointmentEntity>(AppointmentTable) {

        fun new(request: AppointmentRequest) = new {
            userId = request.userId
            businessId = EntityID(request.businessId, table = AppointmentBusinessTable)
            employeeId = request.employee.id
            employeeUserId = request.employee.userId
            employeeName = request.employee.fullName
            clientId = request.client.id
            clientName = request.client.fullName
            clientPhone = request.client.phone
            clientEmail = request.client.email
            dateStart = request.date
            dateEnd = request.dateEnd
            note = request.note
            status = AppointmentStatus.SCHEDULED
            cancellationReason = ""
        }

        fun new(appointment: Appointment) = new {
            userId = appointment.userId
            businessId = EntityID(appointment.businessId, table = AppointmentBusinessTable)
            employeeId = appointment.employee.id
            employeeUserId = appointment.employee.userId
            employeeName = appointment.employee.fullName
            clientId = appointment.client.id
            clientName = appointment.client.fullName
            clientPhone = appointment.client.phone
            clientEmail = appointment.client.email
            dateStart = appointment.date
            dateEnd = appointment.dateEnd
            note = appointment.note
            status = AppointmentStatus.SCHEDULED
            cancellationReason = ""
        }

        fun findByIdAndUpdate(appointment: Appointment) = findByIdAndUpdate(appointment.id) {
            it.userId = appointment.userId
            it.businessId = EntityID(appointment.businessId, table = AppointmentBusinessTable)
            it.employeeId = appointment.employee.id
            it.employeeUserId = appointment.employee.userId
            it.employeeName = appointment.employee.fullName
            it.clientId = appointment.client.id
            it.clientName = appointment.client.fullName
            it.clientPhone = appointment.client.phone
            it.clientEmail = appointment.client.email
            it.dateStart = appointment.date
            it.dateEnd = appointment.dateEnd
            it.note = appointment.note
            it.updatedAt = Clock.System.now()
        }
    }
}

internal fun List<AppointmentEntity>.withDetails(): List<AppointmentEntity> =
    this.with(AppointmentEntity::services, AppointmentEntity::priceAdjustments, PriceAdjustmentEntity::additionalServices)
