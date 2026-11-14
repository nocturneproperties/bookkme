package com.bookk.appointments.data.orm.entity

import com.bookk.appointments.data.datasource.SnapshotReader
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

    var initiatorUserId by AppointmentTable.initiatorUserId
    var businessId by AppointmentTable.businessId
    var employeeId by AppointmentTable.employeeId
    var employeeUserId by AppointmentTable.employeeUserId
    var employeeName by AppointmentTable.employeeName
    var employeeNameEncrypted by AppointmentTable.employeeNameEncrypted
    var clientId by AppointmentTable.clientId
    var clientUserId by AppointmentTable.clientUserId
    var clientName by AppointmentTable.clientName
    var clientNameEncrypted by AppointmentTable.clientNameEncrypted
    var clientPhone by AppointmentTable.clientPhone
    var clientPhoneEncrypted by AppointmentTable.clientPhoneEncrypted
    var clientEmail by AppointmentTable.clientEmail
    var clientEmailEncrypted by AppointmentTable.clientEmailEncrypted
    val services by AppointmentServiceEntity referrersOn AppointmentServicesTable.appointmentId
    val priceAdjustments by PriceAdjustmentEntity referrersOn PriceAdjustmentTable.appointmentId
    var dateStart by AppointmentTable.dateStart
    var dateEnd by AppointmentTable.dateEnd
    var note by AppointmentTable.note
    var noteEncrypted by AppointmentTable.noteEncrypted
    var status by AppointmentTable.status
    var cancellationReason by AppointmentTable.cancellationReason
    var completedBy by AppointmentTable.completedBy
    var updatedAt by AppointmentTable.updatedAt

    fun domain(reader: SnapshotReader): Appointment {
        val snapshot = storedSnapshot()
        val client = reader.client(snapshot)
        return Appointment(
            id = id.value,
            initiatorUserId = initiatorUserId,
            businessId = businessId.value,
            employee = EmployeeSnapshot(
                id = employeeId,
                userId = employeeUserId,
                fullName = reader.employeeName(snapshot),
            ),
            client = ClientSnapshot(
                id = clientId,
                fullName = client.fullName,
                phone = client.phone,
                email = client.email,
                userId = clientUserId,
            ),
            services = services.map {
                ServiceSnapshot(
                    id = it.serviceId,
                    name = it.serviceName,
                    groupId = it.serviceGroupId,
                    price = Money.of(
                        CurrencyUnit.of(it.priceCurrency),
                        BigDecimal(BigInteger.valueOf(it.priceUnscaled), it.priceScale),
                    ),
                    duration = it.durationMinutes.minutes,
                )
            },
            date = dateStart,
            note = client.note,
            status = status,
            cancellationReason = cancellationReason,
            completedBy = completedBy,
            priceAdjustment = priceAdjustments.singleOrNull()?.domain(),
        )
    }

    fun replacePriceAdjustment(adjustment: PriceAdjustment) {
        clearPriceAdjustment()
        PriceAdjustmentEntity.new(id, adjustment)
    }

    fun clearPriceAdjustment() {
        val entityId = id
        PriceAdjustmentTable.deleteWhere { PriceAdjustmentTable.appointmentId eq entityId }
    }

    fun storedSnapshot(): StoredSnapshot {
        return StoredSnapshot(
            clientId = clientId,
            employeeUserId = employeeUserId,
            plaintextClient = clientName?.let { ClientDetails(it, clientPhone, clientEmail, note.orEmpty()) },
            encryptedClient = clientNameEncrypted?.let {
                EncryptedClientDetails(
                    fullName = it,
                    phone = clientPhoneEncrypted,
                    email = clientEmailEncrypted,
                    note = checkNotNull(noteEncrypted) { "Snapshot of client $clientId has no encrypted note" },
                )
            },
            plaintextEmployeeName = employeeName,
            encryptedEmployeeName = employeeNameEncrypted,
        )
    }

    fun store(encrypted: EncryptedSnapshot) {
        storeClient(encrypted.client)
        employeeNameEncrypted = encrypted.employeeName
        employeeName = null
    }

    fun storeClient(encrypted: EncryptedClientDetails?) {
        clientNameEncrypted = encrypted?.fullName
        clientPhoneEncrypted = encrypted?.phone
        clientEmailEncrypted = encrypted?.email
        noteEncrypted = encrypted?.note
        clientName = null
        clientPhone = null
        clientEmail = null
        note = null
    }

    companion object : DecoratorUuidEntityClass<AppointmentEntity>(AppointmentTable) {

        fun new(request: AppointmentRequest, encrypted: EncryptedSnapshot) = new {
            initiatorUserId = request.initiatorUserId
            businessId = EntityID(request.businessId, table = AppointmentBusinessTable)
            employeeId = request.employee.id
            employeeUserId = request.employee.userId
            clientId = request.client.id
            clientUserId = request.client.userId
            dateStart = request.date
            dateEnd = request.dateEnd
            store(encrypted)
            status = AppointmentStatus.SCHEDULED
            cancellationReason = ""
        }

        fun new(appointment: Appointment, encrypted: EncryptedSnapshot) = new {
            initiatorUserId = appointment.initiatorUserId
            businessId = EntityID(appointment.businessId, table = AppointmentBusinessTable)
            employeeId = appointment.employee.id
            employeeUserId = appointment.employee.userId
            clientId = appointment.client.id
            clientUserId = appointment.client.userId
            dateStart = appointment.date
            dateEnd = appointment.dateEnd
            store(encrypted)
            status = AppointmentStatus.SCHEDULED
            cancellationReason = ""
        }

        fun findByIdAndUpdate(appointment: Appointment, encrypted: EncryptedSnapshot) = findByIdAndUpdate(
            appointment.id,
        ) {
            it.initiatorUserId = appointment.initiatorUserId
            it.businessId = EntityID(appointment.businessId, table = AppointmentBusinessTable)
            it.employeeId = appointment.employee.id
            it.employeeUserId = appointment.employee.userId
            it.clientId = appointment.client.id
            it.clientUserId = appointment.client.userId
            it.dateStart = appointment.date
            it.dateEnd = appointment.dateEnd
            it.store(encrypted)
            it.updatedAt = Clock.System.now()
        }
    }
}

internal fun List<AppointmentEntity>.withDetails(): List<AppointmentEntity> = this.with(
    AppointmentEntity::services,
    AppointmentEntity::priceAdjustments,
    PriceAdjustmentEntity::additionalServices,
)
