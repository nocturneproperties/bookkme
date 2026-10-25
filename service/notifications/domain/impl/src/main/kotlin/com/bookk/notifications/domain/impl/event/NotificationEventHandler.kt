package com.bookk.notifications.domain.impl.event

import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.core.data.eventstreaming.StandardEventConsumer
import com.bookk.core.data.eventstreaming.registerResultReceiver
import com.bookk.notifications.domain.api.CreateDeviceEntry
import com.bookk.notifications.domain.api.DeleteDeviceByUUID
import com.bookk.notifications.domain.api.DeleteUserNotificationData
import com.bookk.notifications.domain.impl.UpdateDeviceLanguage
import com.bookk.notifications.domain.impl.UpdateTargetInformation
import com.bookk.notifications.domain.impl.UpdateTargetInformation.Target
import com.bookk.notifications.domain.impl.notification.SendNotification
import com.bookk.notifications.domain.impl.notification.renderer.appointment.notification
import com.bookk.notifications.domain.impl.notification.renderer.employee.notification
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.auth.client.AuthEvent
import com.bookk.server.business.client.api.event.BusinessEvent
import com.bookk.server.user.client.api.event.UserEvent
import kotlinx.coroutines.CoroutineScope

internal class NotificationEventHandler(
    private val consumer: StandardEventConsumer,
    private val createDeviceEntry: CreateDeviceEntry,
    private val deleteDeviceByUUID: DeleteDeviceByUUID,
    private val updateTargetInformation: UpdateTargetInformation,
    private val updateDeviceLanguage: UpdateDeviceLanguage,
    private val sendNotification: SendNotification,
    private val deleteUserNotificationData: DeleteUserNotificationData
) : EventHandler {
    override fun start(scope: CoroutineScope) {
        consumer
            .registerResultReceiver(AuthEvent.DeviceCreated.TOPIC) { event: AuthEvent.DeviceCreated ->
                createDeviceEntry(event.deviceUuid, event.authId, event.userId, event.language)
            }
            .registerResultReceiver(AuthEvent.UserDeleted.TOPIC) { event: AuthEvent.UserDeleted ->
                deleteUserNotificationData(event.userId)
            }
            .registerResultReceiver(AuthEvent.DeviceLanguageUpdated.TOPIC) { event: AuthEvent.DeviceLanguageUpdated ->
                updateDeviceLanguage(event.deviceUuid, event.language)
            }
            .registerResultReceiver(AuthEvent.DeviceDeleted.TOPIC) { event: AuthEvent.DeviceDeleted ->
                deleteDeviceByUUID(event.deviceUuid)
            }
            .registerResultReceiver(UserEvent.Updated.TOPIC) { event : UserEvent.Updated ->
                updateTargetInformation(event.userId, Target.Email(event.email), event.updatedAt)
            }
            .registerResultReceiver(AppointmentEvent.RequestCreated.TOPIC) { event : AppointmentEvent.RequestCreated ->
                sendNotification(event.employeeUserId, event.notification)
            }
            .registerResultReceiver(AppointmentEvent.RequestApproved.TOPIC) { event : AppointmentEvent.RequestApproved ->
                sendNotification(event.clientUserId, event.notification)
            }
            .registerResultReceiver(AppointmentEvent.RequestRejected.TOPIC) { event : AppointmentEvent.RequestRejected ->
                sendNotification(event.clientUserId, event.notification)
            }
            .registerResultReceiver(AppointmentEvent.Cancelled.TOPIC) { event : AppointmentEvent.Cancelled ->
                sendNotification(event.clientUserId, event.notification)
            }
            .registerResultReceiver(
                BusinessEvent.EmployeeInvitationRedeemed.TOPIC
            ) { event : BusinessEvent.EmployeeInvitationRedeemed ->
                sendNotification(event.inviterUserId, event.notification)
            }
            .start(scope)
    }
}