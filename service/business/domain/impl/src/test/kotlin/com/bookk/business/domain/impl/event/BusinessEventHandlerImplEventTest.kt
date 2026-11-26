package com.bookk.business.domain.impl.event

import com.bookk.business.domain.api.business.operation.DeleteBusiness
import com.bookk.business.domain.api.business.operation.EndBusinessDemo
import com.bookk.business.domain.api.business.operation.SeedDemoBusiness
import com.bookk.business.domain.api.client.operation.RecordClientVisit
import com.bookk.business.domain.api.user.operation.DeleteUserBusinessData
import com.bookk.business.domain.api.user.operation.SyncUserProfile
import com.bookk.core.data.eventstreaming.impl.InMemoryExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.inMemoryEventIdempotencyStorage
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaEventConsumer
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaEventProducer
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaTestBroker
import com.bookk.core.data.eventstreaming.impl.kafka.NoopProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.mockedTransactionManager
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.auth.client.AuthEvent
import com.bookk.server.user.client.api.event.UserEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class BusinessEventHandlerImplEventTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun startBroker() {
            KafkaTestBroker.servers
            KafkaTestBroker.createTopic(UserEvent.Updated.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(AuthEvent.UserDeleted.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(AuthEvent.DemoUpgraded.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(AuthEvent.DemoAccountCreated.TOPIC, partitions = 3)
            KafkaTestBroker.createTopic(AppointmentEvent.ClientVisitBooked.TOPIC, partitions = 3)
        }
    }

    private class SutFixture {
        val deleteBusiness = mockk<DeleteBusiness>()
        val syncUserProfile = mockk<SyncUserProfile>()
        val deleteUserBusinessData = mockk<DeleteUserBusinessData>()
        val recordClientVisit = mockk<RecordClientVisit>()
        val endBusinessDemo = mockk<EndBusinessDemo>()
        val seedDemoBusiness = mockk<SeedDemoBusiness>()
        val transactionManager = mockedTransactionManager()
        val consumer = KafkaEventConsumer(
            servers = KafkaTestBroker.servers,
            consumerGroup = "business-handler-${Uuid.random()}",
            eventIdempotencyStorage = inMemoryEventIdempotencyStorage(),
            protoBuf = KafkaTestBroker.protoBuf,
            dltProducer = NoopProducer(),
            exhaustedEventDataSource = InMemoryExhaustedEventDataSource(transactionManager),
            transactionManager = transactionManager,
        )
        val sut =
            BusinessEventHandlerImpl(
                consumer,
                deleteBusiness,
                syncUserProfile,
                deleteUserBusinessData,
                recordClientVisit,
                endBusinessDemo,
                seedDemoBusiness,
            )

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun start() = sut.start(scope)

        fun stop() = scope.cancel()
    }

    private fun producer() = KafkaEventProducer(KafkaTestBroker.servers, "handler-test", KafkaTestBroker.protoBuf)

    @Test
    fun `should seed the sample business of a new demo account`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val arrived = CountDownLatch(1)
        coEvery { fixture.seedDemoBusiness(userId, "Europe/Kyiv", Language.UK) } answers {
            arrived.countDown()
            Result.success(Unit)
        }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(
                AuthEvent.DemoAccountCreated(
                    userId = userId,
                    expiresAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
                    timeZoneId = "Europe/Kyiv",
                    language = Language.UK,
                ),
            )
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) { fixture.seedDemoBusiness(userId, "Europe/Kyiv", Language.UK) }
    }

    @Test
    fun `should end the demo of the businesses of an upgraded demo account`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val arrived = CountDownLatch(1)
        coEvery { fixture.endBusinessDemo(userId) } answers {
            arrived.countDown()
            Result.success(Unit)
        }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(AuthEvent.DemoUpgraded(userId))
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) { fixture.endBusinessDemo(userId) }
    }

    @Test
    fun `should sync a user profile published by the user service`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val arrived = CountDownLatch(1)
        coEvery {
            fixture.syncUserProfile(userId, any(), any(), any(), any(), any(), any())
        } answers {
            arrived.countDown()
            Result.success(Unit)
        }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(
                UserEvent.Updated(
                    userId = userId,
                    name = "Jane",
                    lastName = "Smith",
                    email = "jane@example.com",
                    phone = "+10000000000",
                    updatedAt = updatedAt,
                    telegramUserId = 42,
                    telegramUsername = "jane_smith",
                ),
            )
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) {
            fixture.syncUserProfile(
                userId,
                "Jane",
                "Smith",
                "jane@example.com",
                "+10000000000",
                "jane_smith",
                updatedAt,
            )
        }
    }

    @Test
    fun `should carry a missing phone through as null`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val arrived = CountDownLatch(1)
        coEvery {
            fixture.syncUserProfile(userId, any(), any(), any(), any(), any(), any())
        } answers {
            arrived.countDown()
            Result.success(Unit)
        }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(
                UserEvent.Updated(
                    userId = userId,
                    name = "Jane",
                    lastName = "Smith",
                    email = "jane@example.com",
                    phone = null,
                    updatedAt = updatedAt,
                    telegramUserId = null,
                    telegramUsername = null,
                ),
            )
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) {
            fixture.syncUserProfile(userId, "Jane", "Smith", "jane@example.com", null, null, updatedAt)
        }
    }

    @Test
    fun `should delete the business and the user's business data when auth deletes the user`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val arrived = CountDownLatch(1)
        coEvery { fixture.deleteBusiness(userId) } returns Result.success(Unit)
        coEvery {
            fixture.deleteUserBusinessData(userId)
        } answers {
            arrived.countDown()
            Result.success(Unit)
        }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(AuthEvent.UserDeleted(userId = userId))
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        assertTrue(arrived.count == 0L)
        coVerify(exactly = 1) { fixture.deleteBusiness(userId) }
        coVerify(exactly = 1) { fixture.deleteUserBusinessData(userId) }
    }

    @Test
    fun `should record a client visit booked in the appointments service`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        val visitEnd = Instant.fromEpochMilliseconds(1_900_000_000_000)
        val arrived = CountDownLatch(1)
        coEvery { fixture.recordClientVisit(businessId, clientId, visitEnd) } answers {
            arrived.countDown()
            Result.success(Unit)
        }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(AppointmentEvent.ClientVisitBooked(businessId, clientId, visitEnd))
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) { fixture.recordClientVisit(businessId, clientId, visitEnd) }
    }
}
