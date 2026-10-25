package com.bookk.appointments.domain.impl.event

import com.bookk.appointments.domain.api.operation.DeleteModule
import com.bookk.appointments.domain.api.operation.DeleteUserAppointmentData
import com.bookk.appointments.domain.impl.operation.SyncEmployeePermission
import com.bookk.appointments.domain.impl.operation.UpdateBusinessInformation
import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.core.data.eventstreaming.impl.InMemoryExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.inMemoryEventIdempotencyStorage
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaEventConsumer
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaEventProducer
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaTestBroker
import com.bookk.core.data.eventstreaming.impl.kafka.NoopProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AuthEvent
import com.bookk.server.business.client.api.BusinessDTO
import com.bookk.server.business.client.api.event.BusinessEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import library.permissions.ResourcePermission
import library.schedule.DayOffRange
import library.schedule.Schedule
import library.schedule.WorkHour
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AppointmentEventHandlerEventTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun startBroker() {
            KafkaTestBroker.servers
            KafkaTestBroker.createTopic(BusinessEvent.Updated.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(BusinessEvent.Deleted.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(AuthEvent.UserDeleted.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(BusinessEvent.EmployeePermissionsChanged.TOPIC, partitions = 3)
        }
    }

    private class SutFixture {
        val deleteModule = mockk<DeleteModule>()
        val updateBusinessInformation = mockk<UpdateBusinessInformation>()
        val deleteUserAppointmentData = mockk<DeleteUserAppointmentData>()
        val syncEmployeePermission = mockk<SyncEmployeePermission>()
        val consumer = KafkaEventConsumer(
            servers = KafkaTestBroker.servers,
            consumerGroup = "appointment-handler-${Uuid.random()}",
            eventIdempotencyStorage = inMemoryEventIdempotencyStorage(),
            protoBuf = KafkaTestBroker.protoBuf,
            dltProducer = NoopProducer(),
            exhaustedEventDataSource = InMemoryExhaustedEventDataSource()
        )
        val sut = AppointmentEventHandler(
            consumer,
            deleteModule,
            updateBusinessInformation,
            deleteUserAppointmentData,
            syncEmployeePermission
        )
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun start() = sut.start(scope)

        fun stop() = scope.cancel()
    }

    private fun producer() = KafkaEventProducer(KafkaTestBroker.servers, "appointment-test", KafkaTestBroker.protoBuf)

    private val updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun businessDto(businessId: Uuid, schedule: Schedule) = BusinessDTO(
        id = businessId,
        name = "Salon",
        address = "1 Main St",
        timeZone = TimeZone.of("Europe/Kyiv"),
        schedule = schedule
    )

    @Test
    fun `should replicate a business schedule published by the business service`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val schedule = Schedule(
            workingDays = listOf(DayOfWeek.SATURDAY),
            workingHours = mapOf(DayOfWeek.SATURDAY to listOf(WorkHour(LocalTime(10, 0), LocalTime(14, 0)))),
            dayOffs = listOf(DayOffRange(LocalDate(2099, 12, 30), LocalDate(2099, 12, 31)))
        )
        val arrived = CountDownLatch(1)
        coEvery {
            fixture.updateBusinessInformation(any(), any())
        } answers { arrived.countDown(); Result.success(Unit) }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(BusinessEvent.Updated(businessDto(businessId, schedule), updatedAt))
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) {
            fixture.updateBusinessInformation(
                match { it.id == businessId && it.schedule == schedule && it.timeZone == TimeZone.of("Europe/Kyiv") },
                updatedAt
            )
        }
    }

    @Test
    fun `should carry the schedule day offs across the wire`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val dayOffs = listOf(DayOffRange(LocalDate(2099, 1, 1), LocalDate(2099, 1, 5)))
        val schedule = Schedule().copy(dayOffs = dayOffs)
        val arrived = CountDownLatch(1)
        coEvery {
            fixture.updateBusinessInformation(any(), any())
        } answers { arrived.countDown(); Result.success(Unit) }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(BusinessEvent.Updated(businessDto(businessId, schedule), updatedAt))
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) {
            fixture.updateBusinessInformation(match { it.schedule.dayOffs == dayOffs }, updatedAt)
        }
    }

    @Test
    fun `should delete the module when the business is deleted`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val arrived = CountDownLatch(1)
        coEvery { fixture.deleteModule(businessId) } answers { arrived.countDown(); Result.success(Unit) }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(BusinessEvent.Deleted(businessId = businessId))
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) { fixture.deleteModule(businessId) }
    }

    @Test
    fun `should delete user appointment data when the auth service deletes the user`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val arrived = CountDownLatch(1)
        coEvery { fixture.deleteUserAppointmentData(userId) } answers { arrived.countDown(); Result.success(Unit) }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(AuthEvent.UserDeleted(userId = userId))
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) { fixture.deleteUserAppointmentData(userId) }
    }

    @Test
    fun `should sync employee permission when the business service publishes a permission change`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val employeeUserId = Uuid.random()
        val businessId = Uuid.random()
        val permissions = BusinessPermissions.stub(appointments = ResourcePermission(view = true, update = false, delete = false))
        val arrived = CountDownLatch(1)
        coEvery {
            fixture.syncEmployeePermission(employeeUserId, businessId, permissions.appointments, false)
        } answers { arrived.countDown(); Result.success(Unit) }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(BusinessEvent.EmployeePermissionsChanged(employeeUserId, businessId, permissions))
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) { fixture.syncEmployeePermission(employeeUserId, businessId, permissions.appointments, false) }
    }

    @Test
    fun `should sync the suspension when the business service publishes a suspended employee`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val employeeUserId = Uuid.random()
        val businessId = Uuid.random()
        val permissions = BusinessPermissions.NONE
        val arrived = CountDownLatch(1)
        coEvery {
            fixture.syncEmployeePermission(employeeUserId, businessId, permissions.appointments, true)
        } answers { arrived.countDown(); Result.success(Unit) }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(BusinessEvent.EmployeePermissionsChanged(employeeUserId, businessId, permissions, suspended = true))
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) { fixture.syncEmployeePermission(employeeUserId, businessId, permissions.appointments, true) }
    }
}
