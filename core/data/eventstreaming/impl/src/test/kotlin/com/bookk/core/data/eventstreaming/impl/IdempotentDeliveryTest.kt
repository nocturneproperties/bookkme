package com.bookk.core.data.eventstreaming.impl

import com.bookk.core.data.eventstreaming.EventClaim
import com.bookk.core.data.eventstreaming.EventIdempotencyStorage
import com.bookk.core.data.eventstreaming.impl.kafka.KeyedTestEvent
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.util.logging.KtorSimpleLogger
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.testTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class IdempotentDeliveryTest {

    private class SutFixture {
        val storage = mockk<EventIdempotencyStorage>()
        val claimTtl = 1.minutes
        val event = KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = "topic.delivery")
        val handled = AtomicInteger(0)
        val sut = IdempotentDelivery("business_group", storage, KtorSimpleLogger("IdempotentDeliveryTest"))

        init {
            every { storage.claimTtl } returns claimTtl
            coEvery { storage.markProcessed(any(), any(), any()) } just Runs
            coEvery { storage.release(any(), any(), any(), any()) } just Runs
        }

        fun claimReturns(vararg claims: EventClaim) {
            coEvery { storage.claim("business_group", event.topic, event.idempotencyKey, any()) } returnsMany claims.toList()
        }

        suspend fun deliver(onEvent: suspend (KeyedTestEvent) -> Unit = { handled.incrementAndGet() }) =
            sut.deliver(event, onEvent)
    }

    @Test
    fun `should handle a newly claimed event and mark it processed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claimReturns(EventClaim.CLAIMED)

        whenn()
        val result = fixture.deliver()

        then()
        assertEquals(DeliveryOutcome.HANDLED, result.getOrNull())
        assertEquals(1, fixture.handled.get())
        coVerify(exactly = 1) { fixture.storage.markProcessed("business_group", fixture.event.topic, fixture.event.idempotencyKey) }
    }

    @Test
    fun `should skip an already processed event without handling it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claimReturns(EventClaim.ALREADY_PROCESSED)

        whenn()
        val result = fixture.deliver()

        then()
        assertEquals(DeliveryOutcome.SKIPPED_DUPLICATE, result.getOrNull())
        assertEquals(0, fixture.handled.get())
    }

    @Test
    fun `should wait for an unfinished delivery and handle the event once its claim expires`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claimReturns(EventClaim.IN_PROGRESS, EventClaim.IN_PROGRESS, EventClaim.CLAIMED)

        whenn()
        val result = fixture.deliver()

        then()
        assertEquals(DeliveryOutcome.HANDLED, result.getOrNull())
        assertEquals(1, fixture.handled.get())
    }

    @Test
    fun `should skip an event the unfinished delivery completed while waiting`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claimReturns(EventClaim.IN_PROGRESS, EventClaim.ALREADY_PROCESSED)

        whenn()
        val result = fixture.deliver()

        then()
        assertEquals(DeliveryOutcome.SKIPPED_DUPLICATE, result.getOrNull())
        assertEquals(0, fixture.handled.get())
    }

    @Test
    fun `should fail only after the claim stayed in progress for the whole claim ttl`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.storage.claim(any(), any(), any(), any()) } returns EventClaim.IN_PROGRESS

        whenn()
        val result = fixture.deliver()

        then()
        assertTrue(result.exceptionOrNull() is EventClaimedByUnfinishedDelivery)
        assertTrue(currentTime >= fixture.claimTtl.inWholeMilliseconds)
        assertEquals(0, fixture.handled.get())
    }

    @Test
    fun `should release the claim and fail when the handler fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claimReturns(EventClaim.CLAIMED)

        whenn()
        val result = fixture.deliver { throw IllegalStateException("boom") }

        then()
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 1) { fixture.storage.release("business_group", fixture.event.topic, fixture.event.idempotencyKey, any()) }
        coVerify(exactly = 0) { fixture.storage.markProcessed(any(), any(), any()) }
    }

    @Test
    fun `should treat a cancellation thrown by the handler itself as a failure`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claimReturns(EventClaim.CLAIMED)

        whenn()
        val result = fixture.deliver { throw CancellationException("handler timed out") }

        then()
        assertTrue(result.exceptionOrNull() is CancellationException)
        coVerify(exactly = 1) { fixture.storage.release("business_group", fixture.event.topic, fixture.event.idempotencyKey, any()) }
    }

    @Test
    fun `should release the claim and stop when the consumer is cancelled during the handler`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claimReturns(EventClaim.CLAIMED)
        val handlerStarted = CompletableDeferred<Unit>()
        val delivery = launch { fixture.deliver { handlerStarted.complete(Unit); CompletableDeferred<Unit>().await() } }
        handlerStarted.await()

        whenn()
        delivery.cancel()
        runCurrent()

        then()
        assertTrue(delivery.isCancelled)
        coVerify(exactly = 1) { fixture.storage.release("business_group", fixture.event.topic, fixture.event.idempotencyKey, any()) }
    }

    @Test
    fun `should fail without handling the event when the claim cannot be taken`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.storage.claim(any(), any(), any(), any()) } throws IllegalStateException("cache down")

        whenn()
        val result = fixture.deliver()

        then()
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(0, fixture.handled.get())
    }

    @Test
    fun `should report the event handled when marking it processed fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claimReturns(EventClaim.CLAIMED)
        coEvery { fixture.storage.markProcessed(any(), any(), any()) } throws IllegalStateException("cache down")

        whenn()
        val result = fixture.deliver()

        then()
        assertEquals(DeliveryOutcome.HANDLED, result.getOrNull())
        coVerify(exactly = 0) { fixture.storage.release(any(), any(), any(), any()) }
    }

    @Test
    fun `should release the claim under the delivery id it claimed with`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val claimedAs = slot<String>()
        val releasedAs = slot<String>()
        coEvery { fixture.storage.claim(any(), any(), any(), capture(claimedAs)) } returns EventClaim.CLAIMED
        coEvery { fixture.storage.release(any(), any(), any(), capture(releasedAs)) } just Runs

        whenn()
        fixture.deliver { throw IllegalStateException("boom") }

        then()
        assertEquals(claimedAs.captured, releasedAs.captured)
    }

    @Test
    fun `should claim every delivery under its own delivery id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val claimedAs = mutableListOf<String>()
        coEvery { fixture.storage.claim(any(), any(), any(), capture(claimedAs)) } returns EventClaim.ALREADY_PROCESSED

        whenn()
        fixture.deliver()
        fixture.deliver()

        then()
        assertEquals(2, claimedAs.size)
        assertNotEquals(claimedAs[0], claimedAs[1])
    }

    @Test
    fun `should not let a delivery whose claim expired free the claim of the delivery that took over`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val storage = inMemoryEventIdempotencyStorage(testTimeSource)
        val sut = IdempotentDelivery("business_group", storage, KtorSimpleLogger("IdempotentDeliveryTest"))
        val handled = AtomicInteger(0)
        launch {
            sut.deliver(fixture.event) { handled.incrementAndGet(); delay(storage.claimTtl + 30.seconds); throw IllegalStateException("slow failure") }
        }
        runCurrent()
        launch {
            sut.deliver(fixture.event) { handled.incrementAndGet(); delay(storage.claimTtl) }
        }
        advanceTimeBy(storage.claimTtl + 40.seconds)

        whenn()
        val lateDuplicate = async { sut.deliver(fixture.event) { handled.incrementAndGet() } }

        then()
        assertEquals(DeliveryOutcome.SKIPPED_DUPLICATE, lateDuplicate.await().getOrNull())
        assertEquals(2, handled.get())
    }
}
