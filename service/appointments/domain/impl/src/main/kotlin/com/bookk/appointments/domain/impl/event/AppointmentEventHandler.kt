package com.bookk.appointments.domain.impl.event

import com.bookk.appointments.domain.api.operation.DeleteModule
import com.bookk.appointments.domain.api.operation.DeleteUserAppointmentData
import com.bookk.appointments.domain.impl.operation.SyncEmployeePermission
import com.bookk.appointments.domain.impl.operation.UpdateBusinessInformation
import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.core.data.eventstreaming.StandardEventConsumer
import com.bookk.core.data.eventstreaming.registerResultReceiver
import com.bookk.server.auth.client.AuthEvent
import com.bookk.server.business.client.api.event.BusinessEvent
import kotlinx.coroutines.CoroutineScope

internal class AppointmentEventHandler(
    private val consumer: StandardEventConsumer,
    private val deleteModule: DeleteModule,
    private val updateBusinessInformation: UpdateBusinessInformation,
    private val deleteUserAppointmentData: DeleteUserAppointmentData,
    private val syncEmployeePermission: SyncEmployeePermission
) : EventHandler {
    override fun start(scope: CoroutineScope) {
        consumer
            .registerResultReceiver(BusinessEvent.Deleted.TOPIC) { event: BusinessEvent.Deleted ->
                deleteModule(event.businessId)
            }
            .registerResultReceiver(BusinessEvent.Updated.TOPIC) { event: BusinessEvent.Updated ->
                updateBusinessInformation(event.business, event.updatedAt)
            }
            .registerResultReceiver(AuthEvent.UserDeleted.TOPIC) { event: AuthEvent.UserDeleted ->
                deleteUserAppointmentData(event.userId)
            }
            .registerResultReceiver(BusinessEvent.EmployeePermissionsChanged.TOPIC) { event: BusinessEvent.EmployeePermissionsChanged ->
                syncEmployeePermission(event.employeeUserId, event.businessId, event.permissions.appointments, event.suspended)
            }
            .start(scope)
    }
}