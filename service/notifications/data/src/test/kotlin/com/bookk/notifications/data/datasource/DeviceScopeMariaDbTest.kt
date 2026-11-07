package com.bookk.notifications.data.datasource

import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.database.createDatabase
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import kotlin.uuid.Uuid

internal class DeviceScopeMariaDbTest {

    companion object {
        private const val PASSWORD = "test-password"
        private const val PORT = 3306
        private val container = GenericContainer(DockerImageName.parse("mariadb:11.8.3-ubi9"))
            .withEnv("MARIADB_ROOT_PASSWORD", PASSWORD)
            .withExposedPorts(PORT)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*", 2))
        private lateinit var database: Database

        @JvmStatic
        @BeforeAll
        fun startMariaDb() {
            container.start()
            database = createDatabase(
                schemaName = "notifications",
                dbUrl = "mariadb://${container.host}",
                dbPort = container.getMappedPort(PORT).toString(),
                dbUsername = "root",
                dbPassword = PASSWORD,
            )
        }
    }

    private class SutFixture {
        val sut = DeviceDataSourceImpl()
        val transactionManager = ExposedTransactionManager(database)
    }

    @Test
    fun `should converge concurrent creation and replay without losing the current token or language`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val userId = Uuid.random()
        val deviceUuid = Uuid.random()
        val ready = CompletableDeferred<Unit>()

        whenn()
        val created = withContext(Dispatchers.IO) {
            List(8) {
                async {
                    ready.await()
                    fixture.transactionManager.transactionOrThrow { fixture.sut.getOrCreate(authId, deviceUuid, userId, Language.EN) }
                }
            }.also { ready.complete(Unit) }.awaitAll()
        }
        suspendTransaction(db = database) {
            fixture.sut.updateToken(authId, deviceUuid, "current-token")
            fixture.sut.updateLanguage(authId, deviceUuid, Language.UK)
        }
        val replayed = fixture.transactionManager.transactionOrThrow { fixture.sut.getOrCreate(authId, deviceUuid, userId, Language.EN) }

        then()
        assertEquals(1, created.map { it.id }.distinct().size)
        assertEquals(created.first().id, replayed.id)
        assertEquals("current-token", replayed.notificationToken)
        assertEquals(Language.UK, replayed.language)
        assertEquals(listOf(replayed), suspendTransaction(db = database) { fixture.sut.getByUserId(userId) })
    }

    @Test
    fun `should isolate accounts sharing a device with the migrated MariaDB schema`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        val first = suspendTransaction(db = database) { fixture.sut.getOrCreate(Uuid.random(), deviceUuid, Uuid.random(), Language.EN) }
        val second = suspendTransaction(db = database) { fixture.sut.getOrCreate(Uuid.random(), deviceUuid, Uuid.random(), Language.UK) }

        whenn()
        suspendTransaction(db = database) {
            fixture.sut.updateToken(first.authId, deviceUuid, "first-token")
            fixture.sut.updateToken(second.authId, deviceUuid, "second-token")
            fixture.sut.updateLanguage(first.authId, deviceUuid, Language.PL)
            fixture.sut.delete(first.authId, deviceUuid)
            fixture.sut.delete(first.authId, deviceUuid)
        }

        then()
        assertTrue(suspendTransaction(db = database) { fixture.sut.getByUserId(first.userId) }.isEmpty())
        assertEquals(listOf(second.copy(notificationToken = "second-token")), suspendTransaction(db = database) { fixture.sut.getByUserId(second.userId) })
    }

    @Test
    fun `should refuse a conflicting user without changing a stored association`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val device = suspendTransaction(db = database) { fixture.sut.getOrCreate(Uuid.random(), Uuid.random(), Uuid.random(), Language.EN) }

        whenn()
        val result = fixture.transactionManager.transaction { fixture.sut.getOrCreate(device.authId, device.deviceUuid, Uuid.random(), Language.UK) }

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        assertEquals(listOf(device), suspendTransaction(db = database) { fixture.sut.getByUserId(device.userId) })
    }
}
