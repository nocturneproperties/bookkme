package com.bookk.user.data.datasource

import com.bookk.core.data.ExposedTransactionManager
import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.data.database.createDatabase
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.data.cache.UserCacheStrategy.getUser
import com.bookk.user.domain.api.USER_SCHEMA
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.entity.UserEditModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import library.encryption.test.UnkeyedBlindIndex
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

internal class UserProfileCacheMariaDbTest {

    companion object {
        private val SLOWER_THAN_INNODB_TRX_CACHE_REFRESH = 150.milliseconds
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
                schemaName = USER_SCHEMA,
                dbUrl = "mariadb://${container.host}",
                dbPort = container.getMappedPort(PORT).toString(),
                dbUsername = "root",
                dbPassword = PASSWORD,
            )
        }

        @JvmStatic
        @AfterAll
        fun stopMariaDb() {
            container.stop()
        }

        private suspend fun transactionsWaitingForLock(): Long {
            return suspendTransaction(db = database) {
                exec("SELECT COUNT(*) FROM information_schema.INNODB_TRX WHERE trx_state = 'LOCK WAIT'") { rows ->
                    rows.next()
                    rows.getLong(1)
                } ?: 0L
            }
        }
    }

    private class SutFixture {
        val cacheClient = InMemoryCacheClient()
        val transactionManager = ExposedTransactionManager(database)
        val profileCipher = ProfileCipher(InMemorySubjectKeys(), UnkeyedBlindIndex(), RecordingMissingKeys())
        val sut = UserDataSourceImpl(cacheClient, profileCipher)

        suspend fun storedUser(): User {
            return transactionManager.transactionOrThrow { sut.insertNewUser(User.stub(email = randomEmail())) }
        }

        suspend fun readWhileWriteIsHeld(write: suspend () -> Unit, id: Uuid) {
            val written = CompletableDeferred<Unit>()
            val commitWrite = CompletableDeferred<Unit>()
            withContext(Dispatchers.IO) {
                val writer = async {
                    transactionManager.transactionOrThrow {
                        write()
                        written.complete(Unit)
                        commitWrite.await()
                    }
                }
                written.await()
                val reader = async { transactionManager.transactionOrThrow { sut.getUserById(id) } }
                while (!reader.isCompleted && transactionsWaitingForLock() == 0L) {
                    delay(SLOWER_THAN_INNODB_TRX_CACHE_REFRESH)
                }
                delay(SLOWER_THAN_INNODB_TRX_CACHE_REFRESH)
                commitWrite.complete(Unit)
                writer.await()
                reader.await()
            }
        }

        private fun randomEmail(): String {
            return "${Uuid.random()}@example.com"
        }
    }

    @Test
    fun `should not cache a profile read while its account deletion is being written`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val user = fixture.storedUser()

        whenn()
        fixture.readWhileWriteIsHeld(write = { fixture.sut.deleteUser(user.id) }, id = user.id)

        then()
        assertNull(fixture.cacheClient.getUser(user.id))
    }

    @Test
    fun `should not cache the old profile read while an edit is being written`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val user = fixture.storedUser()
        val edit = UserEditModel(id = null, firstName = "Bob", lastName = null, email = null, phone = null)

        whenn()
        fixture.readWhileWriteIsHeld(
            write = { fixture.sut.updateUser(user.id, edit, Clock.System.now()) },
            id = user.id,
        )

        then()
        val cached = fixture.cacheClient.getUser(user.id)
        assertEquals("Bob", cached?.name ?: "Bob")
    }
}
