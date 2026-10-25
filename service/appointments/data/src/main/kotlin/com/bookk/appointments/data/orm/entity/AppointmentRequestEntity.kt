package com.bookk.appointments.data.orm.entity

import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.AppointmentRequestServicesTable
import com.bookk.appointments.data.orm.table.AppointmentRequestTable
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.ClientSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.core.data.DecoratorUuidEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class AppointmentRequestEntity(id: EntityID<Uuid>) : UuidEntity(id) {

    var userId by AppointmentRequestTable.userId
    var businessId by AppointmentRequestTable.businessId
    var employeeId by AppointmentRequestTable.employeeId
    var employeeUserId by AppointmentRequestTable.employeeUserId
    var employeeName by AppointmentRequestTable.employeeName
    var clientId by AppointmentRequestTable.clientId
    var clientName by AppointmentRequestTable.clientName
    var clientPhone by AppointmentRequestTable.clientPhone
    var clientEmail by AppointmentRequestTable.clientEmail
    val services by AppointmentRequestServiceEntity referrersOn AppointmentRequestServicesTable.requestId
    var dateStart by AppointmentRequestTable.dateStart
    var dateEnd by AppointmentRequestTable.dateEnd
    var note by AppointmentRequestTable.note
    var status by AppointmentRequestTable.status
    var declineReason by AppointmentRequestTable.declineReason
    var updatedAt by AppointmentRequestTable.updatedAt

    fun domain(): AppointmentRequest {
        return AppointmentRequest(
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
            declineReason = declineReason
        )
    }

    companion object : DecoratorUuidEntityClass<AppointmentRequestEntity>(AppointmentRequestTable) {

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
            status = AppointmentRequestStatus.PENDING
            declineReason = request.declineReason
        }

        fun findByIdAndUpdate(request: AppointmentRequest) = findByIdAndUpdate(request.id) {
            it.userId = request.userId
            it.businessId = EntityID(request.businessId, table = AppointmentBusinessTable)
            it.employeeId = request.employee.id
            it.employeeUserId = request.employee.userId
            it.employeeName = request.employee.fullName
            it.clientId = request.client.id
            it.clientName = request.client.fullName
            it.clientPhone = request.client.phone
            it.clientEmail = request.client.email
            it.dateStart = request.date
            it.dateEnd = request.dateEnd
            it.note = request.note
            it.status = request.status
            it.declineReason = request.declineReason
            it.updatedAt = Clock.System.now()
        }
    }
}