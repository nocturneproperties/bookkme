package com.bookk.notifications.domain.impl.event

import com.bookk.core.data.eventstreaming.impl.InMemoryExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.inMemoryEventIdempotencyStorage
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaEventConsumer
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaEventProducer
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaTestBroker
import com.bookk.core.data.eventstreaming.impl.kafka.NoopProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.CreateDeviceEntry
import com.bookk.notifications.domain.api.DeleteDeviceByUUID
import com.bookk.notifications.domain.api.DeleteUserNotificationData
import com.bookk.notifications.domain.api.entity.Device
import com.bookk.notifications.domain.impl.UpdateDeviceLanguage
import com.bookk.notifications.domain.impl.UpdateTargetInformation
import com.bookk.notifications.domain.impl.notification.SendNotification
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.auth.client.AuthEvent
import com.bookk.server.business.client.api.event.BusinessEvent
import com.bookk.server.user.client.api.event.UserEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.reflect.typeOf
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class NotificationEventHandlerEventTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun startBroker() {
            KafkaTestBroker.servers
            KafkaTestBroker.createTopic(UserEvent.Updated.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(AuthEvent.DeviceLanguageUpdated.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(AuthEvent.DeviceCreated.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(AuthEvent.DeviceDeleted.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(AuthEvent.UserDeleted.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(AppointmentEvent.RequestCreated.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(AppointmentEvent.RequestApproved.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(AppointmentEvent.RequestRejected.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(AppointmentEvent.Cancelled.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(BusinessEvent.EmployeeInvitationRedeemed.TOPIC, partitions = 3)
        }
    }

    private class SutFixture {
        val createDeviceEntry = mockk<CreateDeviceEntry>()
        val deleteDeviceByUUID = mockk<DeleteDeviceByUUID>()
        val updateTargetInformation = mockk<UpdateTargetInformation>()
        val updateDeviceLanguage = mockk<UpdateDeviceLanguage>()
        val sendNotification = mockk<SendNotification>()
        val deleteUserNotificationData = mockk<DeleteUserNotificationData>()
        val consumer = KafkaEventConsumer(
            servers = KafkaTestBroker.servers,
            consumerGroup = "notification-handler-${Uuid.random()}",
            eventIdempotencyStorage = inMemoryEventIdempotencyStorage(),
            protoBuf = KafkaTestBroker.protoBuf,
            dltProducer = NoopProducer(),
            exhaustedEventDataSource = InMemoryExhaustedEventDataSource()
        )
        val sut = NotificationEventHandler(
            consumer, createDeviceEntry, deleteDeviceByUUID,
            updateTargetInformation, updateDeviceLanguage, sendNotification, deleteUserNotificationData
        )
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun start() = sut.start(scope)

        fun stop() = scope.cancel()
    }

    private fun producer() = KafkaEventProducer(KafkaTestBroker.servers, "notification-test", KafkaTestBroker.protoBuf)

    @Test
    fun `should update the email target from a published user profile`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val arrived = CountDownLatch(1)
        coEvery {
            fixture.updateTargetInformation(userId, any(), any())
        } answers { arrived.countDown(); Result.success(Unit) }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(
                UserEvent.Updated(
                    userId = userId, name = "Jane", lastName = "Smith",
                    email = "jane@example.com", phone = null, updatedAt = updatedAt
                )
            )
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) {
            fixture.updateTargetInformation(
                userId,
                match<UpdateTargetInformation.Target> {
                    it is UpdateTargetInformation.Target.Email && it.newEmail == "jane@example.com"
                },
                updatedAt
            )
        }
    }

    @Test
    fun `should update the device language from a published auth event`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        val arrived = CountDownLatch(1)
        coEvery {
            fixture.updateDeviceLanguage(deviceUuid, any<Language>())
        } answers { arrived.countDown(); Result.success(Unit) }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(AuthEvent.DeviceLanguageUpdated(deviceUuid = deviceUuid, language = Language.EN))
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) { fixture.updateDeviceLanguage(deviceUuid, Language.EN) }
    }

    private val from = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val to = Instant.fromEpochMilliseconds(1_700_003_600_000)

    private fun SutFixture.awaitNotificationTo(target: Uuid): CountDownLatch {
        val arrived = CountDownLatch(1)
        coEvery { sendNotification(target, any()) } answers { arrived.countDown(); Result.success(Unit) }
        return arrived
    }

    private suspend fun <T : com.bookk.core.data.eventstreaming.EventStreaming.Event<String>> publishAndAwait(
        fixture: SutFixture,
        latch: CountDownLatch,
        event: T,
        kType: kotlin.reflect.KType
    ) {
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(event, kType)
            latch.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()
    }

    private fun requestCreated(clientUserId: Uuid, employeeUserId: Uuid) = AppointmentEvent.RequestCreated(
        clientUserId = clientUserId, clientName = "Client", employeeUserId = employeeUserId,
        employeeName = "Employee", from = from, to = to, timeZone = TimeZone.UTC,
        businessName = "Salon", address = "1 Main St", price = "10 USD"
    )

    private fun requestApproved(clientUserId: Uuid, employeeUserId: Uuid) = AppointmentEvent.RequestApproved(
        clientUserId = clientUserId, clientName = "Client", employeeUserId = employeeUserId,
        employeeName = "Employee", from = from, to = to, timeZone = TimeZone.UTC,
        businessName = "Salon", address = "1 Main St", price = "10 USD"
    )

    private fun requestRejected(clientUserId: Uuid, employeeUserId: Uuid) = AppointmentEvent.RequestRejected(
        clientUserId = clientUserId, clientName = "Client", employeeUserId = employeeUserId,
        employeeName = "Employee", from = from, to = to, timeZone = TimeZone.UTC,
        address = "1 Main St", businessName = "Salon", price = "10 USD", declineReason = "busy"
    )

    private fun cancelled(clientUserId: Uuid, employeeUserId: Uuid) = AppointmentEvent.Cancelled(
        clientUserId = clientUserId, clientName = "Client", employeeUserId = employeeUserId,
        employeeName = "Employee", from = from, to = to, timeZone = TimeZone.UTC,
        address = "1 Main St", businessName = "Salon", price = "10 USD", reason = "changed plans"
    )

    @Test
    fun `should create a device entry from a published auth event`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        val authId = Uuid.random()
        val userId = Uuid.random()
        val arrived = CountDownLatch(1)
        coEvery {
            fixture.createDeviceEntry(deviceUuid, authId, userId, any())
        } answers { arrived.countDown(); Result.success(Device.stub(deviceId = deviceUuid)) }

        whenn()
        publishAndAwait(
            fixture, arrived,
            AuthEvent.DeviceCreated(authId = authId, userId = userId, deviceUuid = deviceUuid, language = Language.EN),
            typeOf<AuthEvent.DeviceCreated>()
        )

        then()
        coVerify(exactly = 1) { fixture.createDeviceEntry(deviceUuid, authId, userId, Language.EN) }
    }

    @Test
    fun `should delete a device from a published auth event`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        val arrived = CountDownLatch(1)
        coEvery { fixture.deleteDeviceByUUID(deviceUuid) } answers { arrived.countDown(); Result.success(Unit) }

        whenn()
        publishAndAwait(
            fixture, arrived,
            AuthEvent.DeviceDeleted(deviceUuid = deviceUuid),
            typeOf<AuthEvent.DeviceDeleted>()
        )

        then()
        coVerify(exactly = 1) { fixture.deleteDeviceByUUID(deviceUuid) }
    }

    @Test
    fun `should notify the employee when an appointment request is created`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val clientUserId = Uuid.random()
        val employeeUserId = Uuid.random()
        val arrived = fixture.awaitNotificationTo(employeeUserId)

        whenn()
        publishAndAwait(
            fixture, arrived,
            requestCreated(clientUserId, employeeUserId),
            typeOf<AppointmentEvent.RequestCreated>()
        )

        then()
        coVerify(exactly = 1) { fixture.sendNotification(employeeUserId, any()) }
        coVerify(exactly = 0) { fixture.sendNotification(clientUserId, any()) }
    }

    @Test
    fun `should notify the client when an appointment request is approved`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val clientUserId = Uuid.random()
        val employeeUserId = Uuid.random()
        val arrived = fixture.awaitNotificationTo(clientUserId)

        whenn()
        publishAndAwait(
            fixture, arrived,
            requestApproved(clientUserId, employeeUserId),
            typeOf<AppointmentEvent.RequestApproved>()
        )

        then()
        coVerify(exactly = 1) { fixture.sendNotification(clientUserId, any()) }
        coVerify(exactly = 0) { fixture.sendNotification(employeeUserId, any()) }
    }

    @Test
    fun `should notify the client when an appointment request is rejected`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val clientUserId = Uuid.random()
        val employeeUserId = Uuid.random()
        val arrived = fixture.awaitNotificationTo(clientUserId)

        whenn()
        publishAndAwait(
            fixture, arrived,
            requestRejected(clientUserId, employeeUserId),
            typeOf<AppointmentEvent.RequestRejected>()
        )

        then()
        coVerify(exactly = 1) { fixture.sendNotification(clientUserId, any()) }
        coVerify(exactly = 0) { fixture.sendNotification(employeeUserId, any()) }
    }

    @Test
    fun `should notify the client when an appointment is cancelled`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val clientUserId = Uuid.random()
        val employeeUserId = Uuid.random()
        val arrived = fixture.awaitNotificationTo(clientUserId)

        whenn()
        publishAndAwait(
            fixture, arrived,
            cancelled(clientUserId, employeeUserId),
            typeOf<AppointmentEvent.Cancelled>()
        )

        then()
        coVerify(exactly = 1) { fixture.sendNotification(clientUserId, any()) }
        coVerify(exactly = 0) { fixture.sendNotification(employeeUserId, any()) }
    }

    @Test
    fun `should notify the inviter when an employee invitation is redeemed`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val inviterUserId = Uuid.random()
        val employeeUserId = Uuid.random()
        val arrived = fixture.awaitNotificationTo(inviterUserId)

        whenn()
        publishAndAwait(
            fixture, arrived,
            BusinessEvent.EmployeeInvitationRedeemed(
                inviterUserId = inviterUserId, employeeUserId = employeeUserId, employeeName = "Employee",
                businessId = Uuid.random(), businessName = "Salon"
            ),
            typeOf<BusinessEvent.EmployeeInvitationRedeemed>()
        )

        then()
        coVerify(exactly = 1) { fixture.sendNotification(inviterUserId, any()) }
        coVerify(exactly = 0) { fixture.sendNotification(employeeUserId, any()) }
    }

    @Test
    fun `should delete user notification data when the auth service deletes the user`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val arrived = CountDownLatch(1)
        coEvery {
            fixture.deleteUserNotificationData(userId)
        } answers { arrived.countDown(); Result.success(Unit) }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(AuthEvent.UserDeleted(userId = userId))
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) { fixture.deleteUserNotificationData(userId) }
    }
}
