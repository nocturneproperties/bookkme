package com.bookk.appointments.data.datasource

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.BookedRange
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.core.data.database.createDatabase
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class EmployeeBookingLockMariaDbTest {

    companion object {
        private val SLOWER_THAN_INNODB_TRX_CACHE_REFRESH = 150.milliseconds
        private const val PASSWORD = "test-password"
        private const val PORT = 3306

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
            database = connect(maxPoolSize = 10)
            waiterDatabase = connect(maxPoolSize = 1)
        }

        private fun connect(maxPoolSize: Int): Database {
            return createDatabase(
                schemaName = "appointments",
                dbUrl = "mariadb://${container.host}",
                dbPort = container.getMappedPort(PORT).toString(),
                dbUsername = "root",
                dbPassword = PASSWORD,
                maxPoolSize = maxPoolSize,
            )
        }
    }

    private class SutFixture {
        val appointmentDataSource = AppointmentDataSourceImpl(testSnapshotCipher())
        val settingsDataSource = AppointmentSettingsDataSourceImpl()
        val subscriptionDataSource = AppointmentSubscriptionDataSourceImpl()
        val employee = EmployeeSnapshot.stub()
        val slotStart: Instant = Instant.parse("2030-06-03T10:00:00Z")
        val slot = slotStart..<slotStart + 30.minutes

        suspend fun attachBusiness(): Uuid {
            val snapshot = BusinessSnapshot.stub()
            suspendTransaction(db = database) {
                subscriptionDataSource.attachBusiness(snapshot)
                settingsDataSource.create(AppointmentSettings.stub(snapshot.id))
            }
            return snapshot.id
        }

        fun booking(businessId: Uuid): AppointmentRequest {
            return AppointmentRequest.stub(businessId = businessId, date = slotStart, employee = employee)
        }

        suspend fun secondBookingCheckWhileFirstBooks(
            firstBusinessId: Uuid,
            secondBusinessId: Uuid,
        ): SecondBookingCheck {
            val firstBooked = CompletableDeferred<BookedRange>()
            val releaseFirst = CompletableDeferred<Unit>()
            return withContext(Dispatchers.IO) {
                val first = async {
                    suspendTransaction(db = database) {
                        requireNotNull(settingsDataSource.getForUpdate(firstBusinessId))
                        val created = appointmentDataSource.create(booking(firstBusinessId))
                        firstBooked.complete(BookedRange(created.id, created.date, created.dateEnd))
                        releaseFirst.await()
                    }
                }
                val booked = firstBooked.await()
                val second = async {
                    runSuspendCatching {
                        suspendTransaction(db = waiterDatabase) {
                            exec("SET SESSION innodb_lock_wait_timeout = 3")
                            requireNotNull(settingsDataSource.getForUpdate(secondBusinessId))
                            appointmentDataSource.getEmployeeBookings(secondBusinessId, employee.id, slot)
                        }
                    }
                }
                val waited = waitedForLock { second.isCompleted }
                releaseFirst.complete(Unit)
                first.await()
                SecondBookingCheck(firstBooking = booked, waited = waited, bookingsSeen = second.await())
            }
        }

        private suspend fun waitedForLock(finished: () -> Boolean): Boolean {
            repeat(POLLS_BEFORE_GIVING_UP) {
                if (finished()) return false
                if (transactionsWaitingForLock() > 0) {
                    delay(SLOWER_THAN_INNODB_TRX_CACHE_REFRESH)
                    return !finished()
                }
                delay(SLOWER_THAN_INNODB_TRX_CACHE_REFRESH)
            }
            return false
        }

        private suspend fun transactionsWaitingForLock(): Long = suspendTransaction(db = database) {
            exec("SELECT COUNT(*) FROM information_schema.INNODB_TRX WHERE trx_state = 'LOCK WAIT'") { rows ->
                rows.next()
                rows.getLong(1)
            } ?: 0L
        }

        private companion object {
            const val POLLS_BEFORE_GIVING_UP = 10
        }
    }

    private class SecondBookingCheck(
        val firstBooking: BookedRange,
        val waited: Boolean,
        val bookingsSeen: Result<List<BookedRange>>,
    )

    @Test
    fun `should make a second booking of the business wait for the first and then see its appointment`() =
        runIntegrationTest {
            given()
            val fixture = SutFixture()
            val businessId = fixture.attachBusiness()

            whenn()
            val check = fixture.secondBookingCheckWhileFirstBooks(businessId, businessId)

            then()
            assertTrue(check.waited)
            assertEquals(listOf(check.firstBooking), check.bookingsSeen.getOrThrow())
        }

    @Test
    fun `should let a booking at another business run while one business books`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val firstBusinessId = fixture.attachBusiness()
        val secondBusinessId = fixture.attachBusiness()

        whenn()
        val check = fixture.secondBookingCheckWhileFirstBooks(firstBusinessId, secondBusinessId)

        then()
        assertFalse(check.waited)
        assertEquals(emptyList<BookedRange>(), check.bookingsSeen.getOrThrow())
    }
}
