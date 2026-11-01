package com.bookk.business.domain.impl.event

import com.bookk.business.domain.api.business.operation.DeleteBusiness
import com.bookk.business.domain.api.user.operation.AnonymizeUserProfile
import com.bookk.business.domain.api.user.operation.SyncUserProfile
import com.bookk.core.data.eventstreaming.impl.InMemoryExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.inMemoryEventIdempotencyStorage
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaEventConsumer
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaEventProducer
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaTestBroker
import com.bookk.core.data.eventstreaming.impl.kafka.NoopProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.mockedTransactionManager
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
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
        }
    }

    private class SutFixture {
        val deleteBusiness = mockk<DeleteBusiness>()
        val syncUserProfile = mockk<SyncUserProfile>()
        val anonymizeUserProfile = mockk<AnonymizeUserProfile>()
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
        val sut = BusinessEventHandlerImpl(consumer, deleteBusiness, syncUserProfile, anonymizeUserProfile)

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun start() = sut.start(scope)

        fun stop() = scope.cancel()
    }

    private fun producer() = KafkaEventProducer(KafkaTestBroker.servers, "handler-test", KafkaTestBroker.protoBuf)

    @Test
    fun `should sync a user profile published by the user service`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val arrived = CountDownLatch(1)
        coEvery {
            fixture.syncUserProfile(userId, any(), any(), any(), any(), any())
        } answers { arrived.countDown(); Result.success(Unit) }

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
                    updatedAt = updatedAt
                )
            )
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) {
            fixture.syncUserProfile(userId, "Jane", "Smith", "jane@example.com", "+10000000000", updatedAt)
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
            fixture.syncUserProfile(userId, any(), any(), any(), any(), any())
        } answers { arrived.countDown(); Result.success(Unit) }

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
                    updatedAt = updatedAt
                )
            )
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) {
            fixture.syncUserProfile(userId, "Jane", "Smith", "jane@example.com", null, updatedAt)
        }
    }

    @Test
    fun `should delete the business and anonymize user data when the auth service deletes the user`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val arrived = CountDownLatch(1)
        coEvery { fixture.deleteBusiness(userId) } returns Result.success(Unit)
        coEvery {
            fixture.anonymizeUserProfile(userId)
        } answers { arrived.countDown(); Result.success(Unit) }

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
        coVerify(exactly = 1) { fixture.anonymizeUserProfile(userId) }
    }
}
