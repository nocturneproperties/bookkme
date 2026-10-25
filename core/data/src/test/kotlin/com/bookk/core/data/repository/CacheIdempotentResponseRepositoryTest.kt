package com.bookk.core.data.repository

import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.yield
import library.idempotency.IdempotencyKey
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KType
import kotlin.time.Duration
import kotlin.uuid.Uuid

internal class CacheIdempotentResponseRepositoryTest {

    private class SutFixture(cacheClient: CacheClient<String> = InMemoryCacheClient()) {
        val sut = CacheIdempotentResponseRepository(cacheClient)
        val resource = "POST /api/business"
        val idempotencyKey = IdempotencyKey(Uuid.random().toString())

        suspend fun lock() = sut.getResponseOrLock(resource, idempotencyKey)
    }

    private class SuspendingAfterReadCacheClient(
        private val delegate: CacheClient<String> = InMemoryCacheClient()
    ) : CacheClient<String> by delegate {
        override suspend fun <V : Any> get(key: String, kType: KType): V? =
            delegate.get<V>(key, kType).also { yield() }
    }

    private class KeyExpiredAfterFirstLockAttemptCacheClient(
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
    fun `should lock a key nobody holds`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val record = fixture.lock()

        then()
        assertNull(record)
    }

    @Test
    fun `should report a locked key as in progress`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.lock()

        whenn()
        val record = fixture.lock()

        then()
        assertTrue(record?.isInProgress == true)
    }

    @Test
    fun `should return the stored response for a key whose request finished`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val response = "stored".toByteArray()
        fixture.lock()
        fixture.sut.storeResponse(fixture.resource, fixture.idempotencyKey, response)

        whenn()
        val record = fixture.lock()

        then()
        assertFalse(record?.isInProgress ?: true)
        assertArrayEquals(response, record?.response)
    }

    @Test
    fun `should let the key be locked again after release`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.lock()
        fixture.sut.release(fixture.resource, fixture.idempotencyKey)

        whenn()
        val record = fixture.lock()

        then()
        assertNull(record)
    }

    @Test
    fun `should give the lock to only one of two concurrent requests with the same key`() = runUnitTest {
        given()
        val fixture = SutFixture(SuspendingAfterReadCacheClient())

        whenn()
        val records = listOf(async { fixture.lock() }, async { fixture.lock() }).awaitAll()

        then()
        assertEquals(1, records.count { it == null })
        assertEquals(1, records.count { it?.isInProgress == true })
    }

    @Test
    fun `should lock again when the key expires between the failed lock and the read`() = runUnitTest {
        given()
        val fixture = SutFixture(KeyExpiredAfterFirstLockAttemptCacheClient())

        whenn()
        val record = fixture.lock()

        then()
        assertNull(record)
    }
}
