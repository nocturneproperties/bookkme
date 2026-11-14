package com.bookk.appointments.data.orm.entity

import com.bookk.appointments.data.datasource.SnapshotReader
import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.AppointmentRequestServicesTable
import com.bookk.appointments.data.orm.table.AppointmentRequestTable
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.RequestClientSnapshot
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

    var initiatorUserId by AppointmentRequestTable.initiatorUserId
    var businessId by AppointmentRequestTable.businessId
    var employeeId by AppointmentRequestTable.employeeId
    var employeeUserId by AppointmentRequestTable.employeeUserId
    var employeeName by AppointmentRequestTable.employeeName
    var employeeNameEncrypted by AppointmentRequestTable.employeeNameEncrypted
    var clientId by AppointmentRequestTable.clientId
    var clientUserId by AppointmentRequestTable.clientUserId
    var clientName by AppointmentRequestTable.clientName
    var clientNameEncrypted by AppointmentRequestTable.clientNameEncrypted
    var clientPhone by AppointmentRequestTable.clientPhone
    var clientPhoneEncrypted by AppointmentRequestTable.clientPhoneEncrypted
    var clientEmail by AppointmentRequestTable.clientEmail
    var clientEmailEncrypted by AppointmentRequestTable.clientEmailEncrypted
    val services by AppointmentRequestServiceEntity referrersOn AppointmentRequestServicesTable.requestId
    var dateStart by AppointmentRequestTable.dateStart
    var dateEnd by AppointmentRequestTable.dateEnd
    var note by AppointmentRequestTable.note
    var noteEncrypted by AppointmentRequestTable.noteEncrypted
    var status by AppointmentRequestTable.status
    var declineReason by AppointmentRequestTable.declineReason
    var updatedAt by AppointmentRequestTable.updatedAt

    fun domain(reader: SnapshotReader): AppointmentRequest {
        val snapshot = storedSnapshot()
        val client = reader.client(snapshot)
        return AppointmentRequest(
            id = id.value,
            initiatorUserId = initiatorUserId,
            businessId = businessId.value,
            employee = EmployeeSnapshot(
                id = employeeId,
                userId = employeeUserId,
                fullName = reader.employeeName(snapshot),
            ),
            client = RequestClientSnapshot(
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
            declineReason = declineReason,
        )
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

    companion object : DecoratorUuidEntityClass<AppointmentRequestEntity>(AppointmentRequestTable) {

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
            status = AppointmentRequestStatus.PENDING
            declineReason = request.declineReason
        }

        fun findByIdAndUpdate(request: AppointmentRequest, encrypted: EncryptedSnapshot) = findByIdAndUpdate(
            request.id,
        ) {
            it.initiatorUserId = request.initiatorUserId
            it.businessId = EntityID(request.businessId, table = AppointmentBusinessTable)
            it.employeeId = request.employee.id
            it.employeeUserId = request.employee.userId
            it.clientId = request.client.id
            it.clientUserId = request.client.userId
            it.dateStart = request.date
            it.dateEnd = request.dateEnd
            it.store(encrypted)
            it.status = request.status
            it.declineReason = request.declineReason
            it.updatedAt = Clock.System.now()
        }
    }
}
