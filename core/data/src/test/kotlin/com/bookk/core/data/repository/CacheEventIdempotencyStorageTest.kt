package com.bookk.core.data.repository

import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.data.eventstreaming.EventClaim
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.reflect.KType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.uuid.Uuid

internal class CacheEventIdempotencyStorageTest {

    private class SutFixture {
        val timeSource = TestTimeSource()
        val sut = CacheEventIdempotencyStorage(InMemoryCacheClient(timeSource = timeSource))
        val topic = "topic.idempotency"
        val idempotencyKey = Uuid.random().toString()
        val delivery = Uuid.random().toString()
        val otherDelivery = Uuid.random().toString()

        suspend fun claim(deliveryId: String = delivery, consumerGroup: String = "business_group", topic: String = this.topic) =
            sut.claim(consumerGroup, topic, idempotencyKey, deliveryId)

        suspend fun markProcessed(consumerGroup: String = "business_group") =
            sut.markProcessed(consumerGroup, topic, idempotencyKey)

        suspend fun release(deliveryId: String = delivery) =
            sut.release("business_group", topic, idempotencyKey, deliveryId)

        fun expireClaim() {
            timeSource += sut.claimTtl + 1.seconds
        }

        fun advanceBy(duration: Duration) {
            timeSource += duration
        }
    }

    private class KeyReleasedAfterFirstClaimAttemptCacheClient(
        private val delegate: CacheClient<String> = InMemoryCacheClient()
    ) : CacheClient<String> by delegate {
        private var firstAttempt = true

        override suspend fun <V : Any> setIfAbsent(key: String, value: V, kType: KType, expiration: Duration?): Boolean {
            if (firstAttempt) {
                firstAttempt = false
                return false
            }
            return delegate.setIfAbsent(key, value, kType, expiration)
        }
    }

    @Test
    fun `should claim an event whose claim was released between the claim attempt and the read`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val sut = CacheEventIdempotencyStorage(KeyReleasedAfterFirstClaimAttemptCacheClient())

        whenn()
        val claim = sut.claim("business_group", fixture.topic, fixture.idempotencyKey, fixture.delivery)

        then()
        assertEquals(EventClaim.CLAIMED, claim)
    }

    @Test
    fun `should claim an event the consumer group has not seen`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val claim = fixture.claim()

        then()
        assertEquals(EventClaim.CLAIMED, claim)
    }

    @Test
    fun `should report an event claimed by the same consumer group as in progress`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claim()

        whenn()
        val claim = fixture.claim(deliveryId = fixture.otherDelivery)

        then()
        assertEquals(EventClaim.IN_PROGRESS, claim)
    }

    @Test
    fun `should report an event marked as processed as already processed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claim()
        fixture.markProcessed()

        whenn()
        val claim = fixture.claim()

        then()
        assertEquals(EventClaim.ALREADY_PROCESSED, claim)
    }

    @Test
    fun `should still report an event as already processed just under an hour after it was processed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claim()
        fixture.markProcessed()
        fixture.advanceBy(1.hours - 1.seconds)

        whenn()
        val claim = fixture.claim(deliveryId = fixture.otherDelivery)

        then()
        assertEquals(EventClaim.ALREADY_PROCESSED, claim)
    }

    @Test
    fun `should forget a processed event an hour after it was processed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claim()
        fixture.markProcessed()
        fixture.advanceBy(1.hours)

        whenn()
        val claim = fixture.claim(deliveryId = fixture.otherDelivery)

        then()
        assertEquals(EventClaim.CLAIMED, claim)
    }

    @Test
    fun `should keep an event processed after another claim attempt`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claim()
        fixture.markProcessed()
        fixture.claim()

        whenn()
        val claim = fixture.claim()

        then()
        assertEquals(EventClaim.ALREADY_PROCESSED, claim)
    }

    @Test
    fun `should let an event be claimed again after its claim is released`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claim()
        fixture.release()

        whenn()
        val claim = fixture.claim()

        then()
        assertEquals(EventClaim.CLAIMED, claim)
    }

    @Test
    fun `should claim an event processed by another consumer group`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claim(consumerGroup = "user_group")
        fixture.markProcessed(consumerGroup = "user_group")

        whenn()
        val claim = fixture.claim()

        then()
        assertEquals(EventClaim.CLAIMED, claim)
    }

    @Test
    fun `should claim the same idempotency key independently on another topic`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claim()

        whenn()
        val claim = fixture.claim(topic = "topic.other")

        then()
        assertEquals(EventClaim.CLAIMED, claim)
    }

    @Test
    fun `should let another delivery claim an event whose claim expired`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claim()
        fixture.expireClaim()

        whenn()
        val claim = fixture.claim(deliveryId = fixture.otherDelivery)

        then()
        assertEquals(EventClaim.CLAIMED, claim)
    }

    @Test
    fun `should keep the claim of the delivery that took over when the delivery whose claim expired releases it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claim()
        fixture.expireClaim()
        fixture.claim(deliveryId = fixture.otherDelivery)

        whenn()
        fixture.release()

        then()
        assertEquals(EventClaim.IN_PROGRESS, fixture.claim(deliveryId = Uuid.random().toString()))
    }

    @Test
    fun `should keep an event processed when the delivery whose claim expired releases it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.claim()
        fixture.expireClaim()
        fixture.claim(deliveryId = fixture.otherDelivery)
        fixture.markProcessed()

        whenn()
        fixture.release()

        then()
        assertEquals(EventClaim.ALREADY_PROCESSED, fixture.claim(deliveryId = Uuid.random().toString()))
    }
}
