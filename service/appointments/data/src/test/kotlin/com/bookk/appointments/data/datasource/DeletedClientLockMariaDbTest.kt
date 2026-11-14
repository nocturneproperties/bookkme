package com.bookk.appointments.data.datasource

import com.bookk.core.data.database.createDatabase
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import kotlin.uuid.Uuid

internal class DeletedClientLockMariaDbTest {

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
                schemaName = "appointments",
                dbUrl = "mariadb://${container.host}",
                dbPort = container.getMappedPort(PORT).toString(),
                dbUsername = "root",
                dbPassword = PASSWORD,
            )
        }
    }

    private class SutFixture {
        val sut = DeletedClientDataSourceImpl()

        suspend fun <T> secondWhileFirstHolds(
            first: suspend DeletedClientDataSourceImpl.() -> Unit,
            second: suspend DeletedClientDataSourceImpl.() -> T,
        ): Result<T> {
            val firstHeld = CompletableDeferred<Unit>()
            val secondFinished = CompletableDeferred<Unit>()
            return withContext(Dispatchers.IO) {
                val holder = async {
                    suspendTransaction(db = database) {
                        sut.first()
                        firstHeld.complete(Unit)
                        secondFinished.await()
                    }
                }
                firstHeld.await()
                val outcome = suspendTransaction(db = database) {
                    exec("SET SESSION innodb_lock_wait_timeout = 1")
                    runSuspendCatching { sut.second() }
                }
                secondFinished.complete(Unit)
                holder.await()
                outcome
            }
        }
    }

    @Test
    fun `should make a link or booking wait for a client deletion that is still being written`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()

        whenn()
        val link = fixture.secondWhileFirstHolds(
            first = { markDeleted(businessId, clientId) },
            second = { isDeletedForShare(businessId, clientId) },
        )

        then()
        assertFalse(link.isSuccess)
    }

    @Test
    fun `should make a client deletion wait for a link or booking that found the client alive`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()

        whenn()
        val deletion = fixture.secondWhileFirstHolds(
            first = { check(!isDeletedForShare(businessId, clientId)) },
            second = { markDeleted(businessId, clientId) },
        )

        then()
        assertFalse(deletion.isSuccess)
    }

    @Test
    fun `should let two links for an already deleted client run side by side`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        suspendTransaction(db = database) { fixture.sut.markDeleted(businessId, clientId) }

        whenn()
        val secondLink = fixture.secondWhileFirstHolds(
            first = {
                check(isDeletedForShare(businessId, clientId))
                markDeleted(businessId, clientId)
            },
            second = {
                check(isDeletedForShare(businessId, clientId))
                markDeleted(businessId, clientId)
            },
        )

        then()
        assertTrue(secondLink.isSuccess)
    }
}
