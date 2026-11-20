package com.bookk.auth.data.datasource

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.core.data.database.createDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.encryption.test.UnkeyedBlindIndex
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import kotlin.random.Random

internal class TelegramIdentityMariaDbTest {

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
                schemaName = "authorization",
                dbUrl = "mariadb://${container.host}",
                dbPort = container.getMappedPort(PORT).toString(),
                dbUsername = "root",
                dbPassword = PASSWORD,
            )
        }
    }

    private class SutFixture {
        val sut = TelegramIdentityDataSourceImpl(UnkeyedBlindIndex())
        val accounts = AccountDataSourceImpl()
        val telegramUserId = Random.nextLong(1, Long.MAX_VALUE)

        suspend fun account(): Authentication = suspendTransaction(db = database) {
            accounts.createAuthorization(Authentication.stub())
        }
    }

    @Test
    fun `should find the linked account through the migrated table`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        suspendTransaction(db = database) { fixture.sut.link(account.id, fixture.telegramUserId) }

        whenn()
        val found = suspendTransaction(db = database) { fixture.sut.getAccountByTelegramUser(fixture.telegramUserId) }

        then()
        assertEquals(account, found)
    }

    @Test
    fun `should refuse a second account for the same Telegram user`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val first = fixture.account()
        val second = fixture.account()
        suspendTransaction(db = database) { fixture.sut.link(first.id, fixture.telegramUserId) }

        whenn()
        val result = runCatching {
            suspendTransaction(db = database) { fixture.sut.link(second.id, fixture.telegramUserId) }
        }

        then()
        assertTrue(result.isFailure)
    }

    @Test
    fun `should drop the link with the deleted account`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        suspendTransaction(db = database) { fixture.sut.link(account.id, fixture.telegramUserId) }

        whenn()
        suspendTransaction(db = database) { fixture.accounts.deleteAuthorization(account.id) }

        then()
        assertNull(
            suspendTransaction(db = database) { fixture.sut.getAccountByTelegramUser(fixture.telegramUserId) },
        )
    }
}
