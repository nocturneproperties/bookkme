package com.bookk.business.domain.impl.export

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.export.BusinessClientExport
import com.bookk.business.domain.api.export.BusinessUserDataExport.DayOffExport
import com.bookk.business.domain.api.export.BusinessUserDataExport.PermissionExport
import com.bookk.business.domain.api.export.BusinessUserDataExport.WorkingHoursExport
import com.bookk.business.domain.api.export.PendingInvitationExport
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.business.domain.datasource.BusinessDataExportDataSource
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.Error
import com.bookk.server.appointments.client.api.export.ExportBusinessAppointments
import com.bookk.server.business.client.api.event.BusinessEvent
import kotlinx.serialization.json.Json
import library.export.DataExportAssembler
import library.schedule.Schedule
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class BusinessDataExportAssembler(
    private val businessDataSource: BusinessDataSource,
    private val serviceDataSource: ServiceDataSource,
    private val employeeDataSource: EmployeeDataSource,
    private val exportDataSource: BusinessDataExportDataSource,
    private val transactionManager: TransactionManager,
    private val exportBusinessAppointments: ExportBusinessAppointments,
    private val eventProducer: StandardEventProducer,
    private val clock: Clock,
) : DataExportAssembler {

    private class OwnData(
        val business: Business,
        val groups: List<ServiceGroup>,
        val services: List<Service>,
        val employees: List<Employee>,
        val pendingInvitations: List<PendingInvitationExport>,
        val clients: List<BusinessClientExport>,
    )

    override suspend fun assemble(ownerId: Uuid): String {
        val own = transactionManager.transactionOrThrow {
            OwnData(
                business = businessDataSource.getBusinessById(ownerId) ?: throw Error.NotFound(),
                groups = serviceDataSource.getServiceGroups(ownerId),
                services = serviceDataSource.getServices(ownerId),
                employees = employeeDataSource.getEmployees(ownerId),
                pendingInvitations = exportDataSource.getPendingInvitations(ownerId),
                clients = exportDataSource.getClients(ownerId),
            )
        }
        val document = BusinessDataExport(
            formatVersion = BusinessDataExport.FORMAT_VERSION,
            generatedAt = clock.now(),
            businessId = ownerId,
            business = own.business.toExport(),
            serviceGroups = own.groups.map { group ->
                BusinessDataExport.ServiceGroupExport(
                    group.id,
                    group.name,
                    own.services.filter {
                        it.group.id ==
                            group.id
                    }.map { it.toExport() },
                )
            },
            employees = own.employees.map { it.toExport(own.business.ownerId) },
            pendingInvitations = own.pendingInvitations.map { invitation ->
                BusinessDataExport.InvitationExport(
                    id = invitation.id,
                    invitedByEmployeeId = own.employees.firstOrNull { it.userId == invitation.invitedBy }?.id,
                    createdAt = invitation.createdAt,
                )
            },
            clients = own.clients,
            appointments = exportBusinessAppointments(ownerId).getOrThrow(),
        )
        return json.encodeToString(document)
    }

    override suspend fun announceReady(ownerId: Uuid, expiresAt: Instant) {
        val business = businessDataSource.getBusinessById(ownerId) ?: return
        eventProducer.send(
            BusinessEvent.ExportReady(businessId = ownerId, ownerUserId = business.ownerId, expiresAt = expiresAt),
        )
    }

    private fun Business.toExport() = BusinessDataExport.ProfileExport(
        name = name,
        description = description,
        address = address,
        latitude = location?.lat,
        longitude = location?.lng,
        currency = currencyCode,
        timeZone = timeZone.id,
        contacts = socials.mapNotNull { social ->
            social.value?.takeIf { it.isNotBlank() }?.let { BusinessDataExport.ContactExport(social.kind.name, it) }
        },
        workingHours = schedule.workingHoursExport(),
        daysOff = schedule.daysOffExport(),
        clientDataRetentionMonths = clientDataRetentionMonths,
    )

    private fun Service.toExport() = BusinessDataExport.ServiceExport(
        id = id,
        name = name,
        price = price.toString(),
        durationMinutes = duration.inWholeMinutes,
        available = isAvailable,
    )

    private fun Employee.toExport(businessOwnerId: Uuid) = BusinessDataExport.EmployeeExport(
        id = id,
        role = if (userId == businessOwnerId) BusinessDataExport.Role.OWNER else BusinessDataExport.Role.EMPLOYEE,
        name = name,
        lastName = lastName,
        phone = phone,
        email = email,
        joinedAt = createdAt,
        suspendedAt = suspendedAt,
        serviceIds = services.map { it.id },
        workingHours = schedule.workingHoursExport(),
        daysOff = schedule.daysOffExport(),
        permissions = permissions.export(),
    )

    private fun Schedule.workingHoursExport() = activeDays().sorted().flatMap { day ->
        this[day].workingTime.map { WorkingHoursExport(day.name, start = it.from, end = it.to) }
    }

    private fun Schedule.daysOffExport() = dayOffs.map { DayOffExport(start = it.start, end = it.end) }

    private fun BusinessPermissions.export() = BusinessResource.entries.map { resource ->
        val granted = this[resource]
        PermissionExport(resource.name, view = granted.view, update = granted.update, delete = granted.delete)
    }

    private companion object {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
