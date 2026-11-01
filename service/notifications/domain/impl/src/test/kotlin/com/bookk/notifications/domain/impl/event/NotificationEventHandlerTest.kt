package com.bookk.notifications.domain.impl.event

import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.StandardEventConsumer
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.CreateDeviceEntry
import com.bookk.notifications.domain.api.DeleteDeviceByUUID
import com.bookk.notifications.domain.api.DeleteUserNotificationData
import com.bookk.notifications.domain.impl.UpdateDeviceLanguage
import com.bookk.notifications.domain.impl.UpdateTargetInformation
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.notifications.domain.impl.notification.SendNotification
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.business.client.api.event.BusinessEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class NotificationEventHandlerTest {

    private class SutFixture {
        val consumer = mockk<StandardEventConsumer>()
        val createDeviceEntry = mockk<CreateDeviceEntry>()
        val deleteDeviceByUUID = mockk<DeleteDeviceByUUID>()
        val updateTargetInformation = mockk<UpdateTargetInformation>()
        val updateDeviceLanguage = mockk<UpdateDeviceLanguage>()
        val sendNotification = mockk<SendNotification>()
        val deleteUserNotificationData = mockk<DeleteUserNotificationData>()
        val receivers = mutableMapOf<String, suspend (Any) -> Unit>()
        val sut = NotificationEventHandler(
            consumer,
            createDeviceEntry,
            deleteDeviceByUUID,
            updateTargetInformation,
            updateDeviceLanguage,
            sendNotification,
            deleteUserNotificationData
        )

        @Suppress("UNCHECKED_CAST")
        fun start(scope: CoroutineScope) {
            every {
                consumer.registerReceiver<EventStreaming.Event<String>>(any(), any(), any())
            } answers {
                receivers[firstArg()] = thirdArg<suspend (Nothing) -> Unit>() as suspend (Any) -> Unit
                consumer
            }
            every { consumer.start(any()) } returns Job()
            sut.start(scope)
        }

        suspend fun dispatch(topic: String, event: Any) {
            val receiver = requireNotNull(receivers[topic]) { "No receiver registered for topic $topic" }
            receiver(event)
        }
    }

    @Test
    fun `should subscribe to employee invitation topics`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.start(this)

        then()
        assertTrue(fixture.receivers.containsKey(BusinessEvent.EmployeeInvitationRedeemed.TOPIC))
    }

    @Test
    fun `should notify the inviter when an invitation is redeemed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = BusinessEvent.EmployeeInvitationRedeemed(
            inviterUserId = Uuid.random(),
            employeeUserId = Uuid.random(),
            employeeName = "Alice",
            businessId = Uuid.random(),
            businessName = "Barbershop"
        )
        val params = slot<NotificationParameters>()
        coEvery { fixture.sendNotification.invoke(any(), capture(params)) } returns Result.success(Unit)
        fixture.start(this)

        whenn()
        fixture.dispatch(BusinessEvent.EmployeeInvitationRedeemed.TOPIC, event)

        then()
        coVerify(exactly = 1) { fixture.sendNotification.invoke(event.inviterUserId, any()) }
        coVerify(exactly = 0) { fixture.sendNotification.invoke(event.employeeUserId, any()) }
        assertEquals(NotificationType.EMPLOYEE, params.captured.type)
    }

    private val from = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val to = Instant.fromEpochMilliseconds(1_700_003_600_000)

    @Test
    fun `should notify nobody when an approved request's client has no account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = AppointmentEvent.RequestApproved(
            clientUserId = null, clientName = "Client", employeeUserId = Uuid.random(), employeeName = "Employee",
            from = from, to = to, timeZone = TimeZone.UTC, businessName = "Salon", address = "1 Main St", price = "10 USD"
        )
        fixture.start(this)

        whenn()
        fixture.dispatch(AppointmentEvent.RequestApproved.TOPIC, event)

        then()
        coVerify(exactly = 0) { fixture.sendNotification.invoke(any(), any()) }
    }

    @Test
    fun `should notify nobody when a rejected request's client has no account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = AppointmentEvent.RequestRejected(
            clientUserId = null, clientName = "Client", employeeUserId = Uuid.random(), employeeName = "Employee",
            from = from, to = to, timeZone = TimeZone.UTC, address = "1 Main St", businessName = "Salon",
            price = "10 USD", declineReason = "busy"
        )
        fixture.start(this)

        whenn()
        fixture.dispatch(AppointmentEvent.RequestRejected.TOPIC, event)

        then()
        coVerify(exactly = 0) { fixture.sendNotification.invoke(any(), any()) }
    }

    @Test
    fun `should notify nobody when a cancelled appointment's client has no account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = AppointmentEvent.Cancelled(
            clientUserId = null, clientName = "Client", employeeUserId = Uuid.random(), employeeName = "Employee",
            from = from, to = to, timeZone = TimeZone.UTC, address = "1 Main St", businessName = "Salon",
            price = "10 USD", reason = "changed plans"
        )
        fixture.start(this)

        whenn()
        fixture.dispatch(AppointmentEvent.Cancelled.TOPIC, event)

        then()
        coVerify(exactly = 0) { fixture.sendNotification.invoke(any(), any()) }
    }
}
