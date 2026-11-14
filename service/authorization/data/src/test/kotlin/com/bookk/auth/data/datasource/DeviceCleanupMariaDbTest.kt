package com.bookk.auth.data.datasource

import com.bookk.auth.data.orm.table.AuthDeviceTable
import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.identification.entity.DeviceIdentity
import com.bookk.core.data.database.createDatabase
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DeviceCleanupMariaDbTest {

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
        val sut = DeviceDataSourceImpl()
        val accounts = AccountDataSourceImpl()
    }

    @Test
    fun `should return deleted devices' owners and keep other accounts on that installation`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        val first = suspendTransaction(db = database) { fixture.accounts.createAuthorization(Authentication.stub()) }
        val second = suspendTransaction(db = database) { fixture.accounts.createAuthorization(Authentication.stub()) }
        val old = Instant.parse("2000-01-01T00:00:00Z")
        suspendTransaction(db = database) {
            fixture.sut.insertDevice(first.id, deviceUuid, "First", Language.EN)
            fixture.sut.insertDevice(second.id, deviceUuid, "Second", Language.EN)
            AuthDeviceTable.update({ AuthDeviceTable.userAuthId eq first.id }) { it[lastLogInAt] = old }
            AuthDeviceTable.update({ AuthDeviceTable.userAuthId eq second.id }) { it[lastLogInAt] = Clock.System.now() }
        }

        whenn()
        val deleted =
            suspendTransaction(db = database) {
                fixture.sut.deleteInactiveDevices(Instant.parse("2020-01-01T00:00:00Z"))
            }

        then()
        assertEquals(listOf(DeviceIdentity(first.id, deviceUuid)), deleted)
        assertNull(suspendTransaction(db = database) { fixture.sut.getDeviceByAuthIdAndUUID(first.id, deviceUuid) })
        assertNotNull(suspendTransaction(db = database) { fixture.sut.getDeviceByAuthIdAndUUID(second.id, deviceUuid) })
    }
}
