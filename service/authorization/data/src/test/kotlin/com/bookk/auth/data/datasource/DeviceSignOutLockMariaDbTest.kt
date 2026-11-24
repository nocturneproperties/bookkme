package com.bookk.auth.data.datasource

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.core.data.database.createDatabase
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.sql.SQLException
import kotlin.uuid.Uuid

internal class DeviceSignOutLockMariaDbTest {

    companion object {
        private const val PASSWORD = "test-password"
        private const val PORT = 3306
        private const val LOCK_WAIT_TIMEOUT = 1205
        private val container = GenericContainer(DockerImageName.parse("mariadb:11.8.3-ubi9"))
            .withEnv("MARIADB_ROOT_PASSWORD", PASSWORD)
            .withExposedPorts(PORT)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*", 2))
        private lateinit var database: Database
        private lateinit var waiterDatabase: Database

        @JvmStatic
        @BeforeAll
        fun startMariaDb() {
            container.start()
            database = connect(maxPoolSize = 4)
            waiterDatabase = connect(maxPoolSize = 1)
        }

        private fun connect(maxPoolSize: Int): Database {
            return createDatabase(
                schemaName = "authorization",
                dbUrl = "mariadb://${container.host}",
                dbPort = container.getMappedPort(PORT).toString(),
                dbUsername = "root",
                dbPassword = PASSWORD,
                maxPoolSize = maxPoolSize,
            )
        }
    }

    private class SutFixture {
        val sut = DeviceDataSourceImpl()
        val accounts = AccountDataSourceImpl()

        suspend fun signedInDevice(): Pair<Uuid, Uuid> {
            val auth = suspendTransaction(db = database) { accounts.createAuthorization(Authentication.stub()) }
            val deviceId = suspendTransaction(db = database) {
                requireNotNull(sut.insertDevice(auth.id, Uuid.random(), "Phone", Language.EN))
            }
            return auth.id to deviceId
        }

        suspend fun <T> secondWhileSignOutLocks(deviceId: Uuid, second: suspend () -> T): Result<T> {
            val firstHeld = CompletableDeferred<Unit>()
            val secondFinished = CompletableDeferred<Unit>()
            return withContext(Dispatchers.IO) {
                val holder = async {
                    suspendTransaction(db = database) {
                        sut.getDeviceByIdForUpdate(deviceId)
                        firstHeld.complete(Unit)
                        secondFinished.await()
                    }
                }
                firstHeld.await()
                val outcome = runSuspendCatching {
                    suspendTransaction(db = waiterDatabase) {
                        exec("SET SESSION innodb_lock_wait_timeout = 1")
                        second()
                    }
                }
                secondFinished.complete(Unit)
                holder.await()
                outcome
            }
        }
    }

    @Test
    fun `should let the account be locked while one of its devices is locked for sign out`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val (authId, deviceId) = fixture.signedInDevice()

        whenn()
        val accountLock = fixture.secondWhileSignOutLocks(deviceId) { fixture.accounts.lockAccount(authId) }

        then()
        assertTrue(accountLock.isSuccess)
    }

    @Test
    fun `should make a second lock of the same device wait for sign out`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val (_, deviceId) = fixture.signedInDevice()

        whenn()
        val deviceLock = fixture.secondWhileSignOutLocks(deviceId) { fixture.sut.getDeviceByIdForUpdate(deviceId) }

        then()
        val lockWait = generateSequence(deviceLock.exceptionOrNull()) { it.cause }
            .filterIsInstance<SQLException>()
            .any { it.errorCode == LOCK_WAIT_TIMEOUT }
        assertTrue(lockWait)
    }
}
