package com.bookk.core.data.cache.impl

import com.bookk.core.data.cache.deleteIfEquals
import com.bookk.core.data.cache.get
import com.bookk.core.data.cache.set
import com.bookk.core.data.cache.setIfAbsent
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class RedisCacheClientTest {

    companion object {
        private const val PASSWORD = "test-password"

        private val container = GenericContainer(DockerImageName.parse("valkey/valkey:8.1-alpine"))
            .withCommand("valkey-server", "--requirepass", PASSWORD)
            .withExposedPorts(6379)

        @JvmStatic
        @BeforeAll
        fun startRedis() {
            container.start()
        }

        private fun connectedClients(): Int {
            val info = container.execInContainer("valkey-cli", "-a", PASSWORD, "--no-auth-warning", "info", "clients").stdout
            return info.lineSequence().first { it.startsWith("connected_clients:") }.substringAfter(':').trim().toInt()
        }
    }

    private class SutFixture {
        val sut = RedisCacheClient(container.host, container.getMappedPort(6379), PASSWORD, ProtoBuf { encodeDefaults = true })
    }

    @Test
    fun `should reuse pooled connections across sequential calls`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val clientsBefore = connectedClients()

        whenn()
        withContext(Dispatchers.IO) {
            repeat(30) { index ->
                val key = "sequential-${Uuid.random()}"
                fixture.sut.set(key, "value-$index", 1.minutes)
                fixture.sut.get<String, String>(key)
                fixture.sut.setIfAbsent(key, "other", 1.minutes)
                fixture.sut.delete(key)
            }
        }

        then()
        assertEquals(1, connectedClients() - clientsBefore)
        fixture.sut.close()
    }

    @Test
    fun `should reuse pooled connections across transactions`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val clientsBefore = connectedClients()

        whenn()
        withContext(Dispatchers.IO) {
            repeat(30) { index ->
                fixture.sut.withTransaction { set("transaction-${Uuid.random()}", "value-$index", 1.minutes) }
            }
        }

        then()
        assertEquals(1, connectedClients() - clientsBefore)
        fixture.sut.close()
    }

    @Test
    fun `should only set the value when the key is absent`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val key = "absent-${Uuid.random()}"

        whenn()
        val first = withContext(Dispatchers.IO) { fixture.sut.setIfAbsent(key, "first", 1.minutes) }
        val second = withContext(Dispatchers.IO) { fixture.sut.setIfAbsent(key, "second", 1.minutes) }
        val stored = withContext(Dispatchers.IO) { fixture.sut.get<String, String>(key) }

        then()
        assertTrue(first)
        assertFalse(second)
        assertEquals("first", stored)
        fixture.sut.close()
    }

    @Test
    fun `should set the value again once the key is deleted`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val key = "deleted-${Uuid.random()}"
        withContext(Dispatchers.IO) { fixture.sut.setIfAbsent(key, "first", 1.minutes) }

        whenn()
        withContext(Dispatchers.IO) { fixture.sut.delete(key) }
        val reserved = withContext(Dispatchers.IO) { fixture.sut.setIfAbsent(key, "again", 1.minutes) }

        then()
        assertTrue(reserved)
        fixture.sut.close()
    }

    @Test
    fun `should keep the key when it holds a value other than the expected one`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val key = "compare-kept-${Uuid.random()}"
        withContext(Dispatchers.IO) { fixture.sut.set(key, "current", 1.minutes) }

        whenn()
        withContext(Dispatchers.IO) { fixture.sut.deleteIfEquals(key, "expected") }

        then()
        assertEquals("current", withContext(Dispatchers.IO) { fixture.sut.get<String, String>(key) })
        fixture.sut.close()
    }

    @Test
    fun `should delete the key when it holds the expected value`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val key = "compare-deleted-${Uuid.random()}"
        withContext(Dispatchers.IO) { fixture.sut.set(key, "expected", 1.minutes) }

        whenn()
        withContext(Dispatchers.IO) { fixture.sut.deleteIfEquals(key, "expected") }

        then()
        assertNull(withContext(Dispatchers.IO) { fixture.sut.get<String, String>(key) })
        fixture.sut.close()
    }

    @Test
    fun `should let exactly one concurrent caller set an absent key`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val key = "race-${Uuid.random()}"

        whenn()
        val winners = withContext(Dispatchers.IO) {
            (1..20).map { index -> async { fixture.sut.setIfAbsent(key, "caller-$index", 1.minutes) } }.awaitAll()
        }

        then()
        assertEquals(1, winners.count { it })
        fixture.sut.close()
    }

    @Test
    fun `should apply every write of a committed transaction`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val firstKey = "commit-first-${Uuid.random()}"
        val secondKey = "commit-second-${Uuid.random()}"
        val deletedKey = "commit-deleted-${Uuid.random()}"
        withContext(Dispatchers.IO) { fixture.sut.set(deletedKey, "stale", 1.minutes) }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.sut.withTransaction {
                set(firstKey, "first", 1.minutes)
                set(secondKey, "second", 1.minutes)
                delete(deletedKey)
            }
        }

        then()
        withContext(Dispatchers.IO) {
            assertEquals("first", fixture.sut.get<String, String>(firstKey))
            assertEquals("second", fixture.sut.get<String, String>(secondKey))
            assertNull(fixture.sut.get<String, String>(deletedKey))
        }
        fixture.sut.close()
    }

    @Test
    fun `should discard queued writes when the transaction action fails`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val key = "discarded-${Uuid.random()}"
        val failure = IllegalStateException("action failed")

        whenn()
        val thrown = withContext(Dispatchers.IO) {
            runCatching {
                fixture.sut.withTransaction {
                    set(key, "never-committed", 1.minutes)
                    throw failure
                }
            }.exceptionOrNull()
        }

        then()
        assertSame(failure, thrown)
        assertNull(withContext(Dispatchers.IO) { fixture.sut.get<String, String>(key) })
        fixture.sut.close()
    }

    @Test
    fun `should reject reading inside a transaction and leave the connection usable`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val key = "read-${Uuid.random()}"

        whenn()
        val thrown = withContext(Dispatchers.IO) {
            runCatching { fixture.sut.withTransaction { get<String, String>(key) } }.exceptionOrNull()
        }

        then()
        assertTrue(thrown is UnsupportedOperationException)
        withContext(Dispatchers.IO) {
            fixture.sut.set(key, "after", 1.minutes)
            assertEquals("after", fixture.sut.get<String, String>(key))
        }
        fixture.sut.close()
    }
}
