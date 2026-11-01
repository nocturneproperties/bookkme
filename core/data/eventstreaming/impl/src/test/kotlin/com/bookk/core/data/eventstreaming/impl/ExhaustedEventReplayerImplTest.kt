package com.bookk.core.data.eventstreaming.impl

import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.ExhaustedEvent
import com.bookk.core.data.eventstreaming.StandardEventConsumer
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.mockedTransactionManager
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

internal class ExhaustedEventReplayerImplTest {

    private class SutFixture(batchSize: Int = ExhaustedEventReplayerImpl.DEFAULT_BATCH_SIZE) {
        val transactionManager = mockedTransactionManager()
        val exhaustedEventDataSource = spyk(InMemoryExhaustedEventDataSource(transactionManager))
        val consumer = mockk<StandardEventConsumer>().apply { coEvery { replay(any()) } returns true }
        val meterRegistry = SimpleMeterRegistry()
        val sut = ExhaustedEventReplayerImpl(
            consumer = consumer,
            exhaustedEventDataSource = exhaustedEventDataSource,
            transactionManager = transactionManager,
            meterRegistry = meterRegistry,
            consumerGroup = GROUP,
            interval = INTERVAL,
            batchSize = batchSize,
        )

        fun parkedGauge(topic: String): Double? = meterRegistry.find(ExhaustedEventReplayerImpl.PARKED_EVENTS_METRIC)
            .tags("consumer_group", GROUP, "topic", topic)
            .gauge()
            ?.value()
    }

    private fun dltEvent(originalTopic: String = TOPIC): DltEvent = ExhaustedEvent.stub(originalTopic = originalTopic).event

    private fun TestScope.nextTick() {
        advanceTimeBy(INTERVAL)
        runCurrent()
    }

    @Test
    fun `should replay every event parked before it started`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = fixture.exhaustedEventDataSource.park(dltEvent())
        val second = fixture.exhaustedEventDataSource.park(dltEvent(originalTopic = OTHER_TOPIC))

        whenn()
        fixture.sut.start(backgroundScope)
        runCurrent()

        then()
        coVerify(exactly = 1) { fixture.consumer.replay(first.event) }
        coVerify(exactly = 1) { fixture.consumer.replay(second.event) }
        assertTrue(fixture.exhaustedEventDataSource.parked().isEmpty())
    }

    @Test
    fun `should not replay an event parked after it started until a replay is requested`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.start(backgroundScope)
        runCurrent()

        whenn()
        val parked = fixture.exhaustedEventDataSource.park(dltEvent())
        nextTick()
        nextTick()

        then()
        coVerify(exactly = 0) { fixture.consumer.replay(any()) }
        assertEquals(listOf(parked), fixture.exhaustedEventDataSource.parked())
        coVerify(exactly = 1) { fixture.exhaustedEventDataSource.requestReplayOfAll() }
    }

    @Test
    fun `should replay an event whose replay was requested by hand on the next tick`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.start(backgroundScope)
        runCurrent()
        val requested = fixture.exhaustedEventDataSource.park(dltEvent(), replayRequested = true)

        whenn()
        nextTick()

        then()
        coVerify(exactly = 1) { fixture.consumer.replay(requested.event) }
        assertTrue(fixture.exhaustedEventDataSource.parked().isEmpty())
    }

    @Test
    fun `should keep an event the consumer cannot replay and stop requesting it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val unreplayable = fixture.exhaustedEventDataSource.park(dltEvent())
        coEvery { fixture.consumer.replay(unreplayable.event) } returns false

        whenn()
        fixture.sut.start(backgroundScope)
        runCurrent()
        nextTick()

        then()
        coVerify(exactly = 1) { fixture.consumer.replay(unreplayable.event) }
        assertEquals(listOf(unreplayable), fixture.exhaustedEventDataSource.parked())
        assertTrue(fixture.exhaustedEventDataSource.replayRequested().isEmpty())
    }

    @Test
    fun `should replay every requested event in batches within one tick`() = runUnitTest {
        given()
        val fixture = SutFixture(batchSize = 2)
        repeat(5) { fixture.exhaustedEventDataSource.park(dltEvent()) }

        whenn()
        fixture.sut.start(backgroundScope)
        runCurrent()

        then()
        coVerify(exactly = 5) { fixture.consumer.replay(any()) }
        assertTrue(fixture.exhaustedEventDataSource.parked().isEmpty())
    }

    @Test
    fun `should keep requested events and retry them on the next tick when replaying fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val parked = fixture.exhaustedEventDataSource.park(dltEvent())
        coEvery { fixture.consumer.replay(parked.event) } throws RuntimeException("broker down") andThen true

        whenn()
        fixture.sut.start(backgroundScope)
        runCurrent()

        then()
        assertEquals(listOf(parked), fixture.exhaustedEventDataSource.replayRequested())
        nextTick()
        assertTrue(fixture.exhaustedEventDataSource.parked().isEmpty())
    }

    @Test
    fun `should request a replay of parked events again on the next tick when the request fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val parked = fixture.exhaustedEventDataSource.park(dltEvent())
        coEvery { fixture.exhaustedEventDataSource.requestReplayOfAll() } throws RuntimeException("database down") andThenAnswer { callOriginal() }

        whenn()
        fixture.sut.start(backgroundScope)
        runCurrent()

        then()
        assertEquals(listOf(parked), fixture.exhaustedEventDataSource.parked())
        nextTick()
        coVerify(exactly = 1) { fixture.consumer.replay(parked.event) }
        assertTrue(fixture.exhaustedEventDataSource.parked().isEmpty())
    }

    @Test
    fun `should replay in a read committed transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.exhaustedEventDataSource.park(dltEvent())

        whenn()
        fixture.sut.start(backgroundScope)
        runCurrent()

        then()
        coVerify { fixture.transactionManager.transaction<Any>(TransactionIsolation.READ_COMMITTED, any()) }
    }

    @Test
    fun `should export the number of parked events per topic`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.consumer.replay(any()) } returns false
        repeat(2) { fixture.exhaustedEventDataSource.park(dltEvent()) }
        fixture.exhaustedEventDataSource.park(dltEvent(originalTopic = OTHER_TOPIC))

        whenn()
        fixture.sut.start(backgroundScope)
        runCurrent()

        then()
        assertEquals(2.0, fixture.parkedGauge(TOPIC))
        assertEquals(1.0, fixture.parkedGauge(OTHER_TOPIC))
    }

    @Test
    fun `should stop exporting a topic once its parked events are replayed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.start(backgroundScope)
        runCurrent()
        fixture.exhaustedEventDataSource.park(dltEvent())
        nextTick()
        assertEquals(1.0, fixture.parkedGauge(TOPIC))
        fixture.transactionManager.transaction { fixture.exhaustedEventDataSource.requestReplayOfAll() }

        whenn()
        nextTick()

        then()
        assertNull(fixture.parkedGauge(TOPIC))
    }

    private companion object {
        const val TOPIC = "replayer.topic"
        const val OTHER_TOPIC = "replayer.other"
        const val GROUP = "appointments_group"
        val INTERVAL = 1.minutes
    }
}
