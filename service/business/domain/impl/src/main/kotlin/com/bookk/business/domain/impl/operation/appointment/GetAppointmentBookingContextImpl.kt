package com.bookk.business.domain.impl.operation.appointment

import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.business.domain.api.appointment.operation.GetAppointmentBookingContext
import com.bookk.business.domain.api.appointment.operation.GetAppointmentBookingContext.Error
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.toRemote
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.business.domain.impl.operation.getServicesExpanded
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.server.business.client.api.event.BusinessEvent
import com.bookk.server.user.client.UserClient
import kotlin.uuid.Uuid

internal class GetAppointmentBookingContextImpl(
    private val employeeDataSource: EmployeeDataSource,
    private val clientDataSource: ClientDataSource,
    private val serviceDataSource: ServiceDataSource,
    private val userClient: UserClient,
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer
) : GetAppointmentBookingContext {
    override suspend fun invoke(
        businessId: Uuid,
        employeeId: Uuid,
        userId: Uuid,
        serviceIds: List<Uuid>
    ): Result<AppointmentBookingContext> {
        if (serviceIds.isEmpty()) return Result.failure(Error.EmptyServiceList())
        return transactionManager.transaction {
            val employee = employeeDataSource.getEmployee(businessId, employeeId) ?: throw Error.EmployeeNotFound()
            if (employee.isSuspended) throw Error.EmployeeSuspended()
            val client = clientDataSource.getClientByUserId(businessId, userId) ?: createAndAttachClientToBusiness(businessId, userId)
            val requestedServices = serviceDataSource.getServicesExpanded(businessId, serviceIds) ?: throw Error.ServiceNotFound()

            AppointmentBookingContext(employee = employee, client = client.toRemote(), services = requestedServices)
        }
    }

    private suspend fun createAndAttachClientToBusiness(businessId: Uuid, userId: Uuid): Client {
        val personalInformation = userClient.getUserById(userId).getOrThrow()
        val profile = Client.Integrated(
            id = Uuid.random(),
            name = personalInformation.name,
            lastName = personalInformation.lastName,
            phone = personalInformation.phone,
            email = personalInformation.email,
            userId = userId
        )
        val sameContactClients = clientDataSource.getClientsByContacts(
            businessId,
            phones = listOfNotNull(profile.phone),
            emails = listOf(personalInformation.email)
        )
            .filterIsInstance<Client.Detached>()
            .sortedByDescending { it.email.equals(personalInformation.email, ignoreCase = true) }
        val replaced = sameContactClients.firstOrNull()
            ?: return clientDataSource.getOrCreateIntegratedClient(businessId, profile)

        sameContactClients.drop(1).forEach { duplicate ->
            clientDataSource.deleteClient(businessId, duplicate.id)
            eventProducer.send(BusinessEvent.ClientMerged(businessId, fromClientId = duplicate.id, toClientId = replaced.id))
        }
        return clientDataSource.takeOverClient(businessId, replaced.id, profile)
            ?: clientDataSource.getOrCreateIntegratedClient(businessId, profile)
    }
}
