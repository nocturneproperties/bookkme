package com.bookk.user.domain.impl.event

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
import com.bookk.user.domain.api.operation.DeleteUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

internal class UserEventHandlerEventTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun startBroker() {
            KafkaTestBroker.servers
            KafkaTestBroker.createTopic(AuthEvent.UserDeleted.TOPIC, partitions = 3)
        }
    }

    private class SutFixture {
        val deleteUser = mockk<DeleteUser>()
        val consumer = KafkaEventConsumer(
            servers = KafkaTestBroker.servers,
            consumerGroup = "user-handler-${Uuid.random()}",
            eventIdempotencyStorage = inMemoryEventIdempotencyStorage(),
            protoBuf = KafkaTestBroker.protoBuf,
            dltProducer = NoopProducer(),
            exhaustedEventDataSource = InMemoryExhaustedEventDataSource()
        )
        val sut = UserEventHandler(consumer, deleteUser)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun start() = sut.start(scope)

        fun stop() = scope.cancel()
    }

    private fun producer() = KafkaEventProducer(KafkaTestBroker.servers, "user-handler-test", KafkaTestBroker.protoBuf)

    @Test
    fun `should delete the user when the auth service deletes the account`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val arrived = CountDownLatch(1)
        coEvery { fixture.deleteUser(userId) } answers { arrived.countDown(); Result.success(Unit) }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.start()
            producer().send(AuthEvent.UserDeleted(userId = userId))
            arrived.await(20, TimeUnit.SECONDS)
        }
        fixture.stop()

        then()
        coVerify(exactly = 1) { fixture.deleteUser(userId) }
    }
}
