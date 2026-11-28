package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.AppointmentRequestServicesTable
import com.bookk.appointments.data.orm.table.AppointmentRequestTable
import com.bookk.appointments.data.orm.table.DayOffsTable
import com.bookk.appointments.data.orm.table.WorkingHoursTable
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.RequestClientSnapshot
import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.encryption.Decrypted
import library.encryption.test.CountingSubjectKeys
import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import library.encryption.test.UnkeyedBlindIndex
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class AppointmentRequestDataSourceImplEncryptionTest {

    private class SutFixture {
        val db = createTestDatabase(
            AppointmentBusinessTable,
            WorkingHoursTable,
            DayOffsTable,
            AppointmentRequestTable,
            AppointmentRequestServicesTable,
        )
        val subjectKeys = InMemorySubjectKeys()
        val countingKeys = CountingSubjectKeys(subjectKeys)
        val missingKeys = RecordingMissingKeys()
        val sut =
            AppointmentRequestDataSourceImpl(
                InMemoryCacheClient(),
                SnapshotCipher(countingKeys, UnkeyedBlindIndex(), missingKeys),
            )
        lateinit var businessId: Uuid

        suspend fun setup() {
            val snapshot = BusinessSnapshot.stub()
            suspendTransaction { AppointmentSubscriptionDataSourceImpl().attachBusiness(snapshot) }
            businessId = snapshot.id
        }

        fun request(client: RequestClientSnapshot = RequestClientSnapshot.stub()): AppointmentRequest {
            return AppointmentRequest.stub(businessId = businessId).copy(client = client, note = "Short hair")
        }

        suspend fun create(request: AppointmentRequest): AppointmentRequest {
            return suspendTransaction { sut.create(request) }
        }

        suspend fun storedRow(id: Uuid): ResultRow {
            return suspendTransaction {
                AppointmentRequestTable.selectAll().where { AppointmentRequestTable.id eq id }.single()
            }
        }

        suspend fun decrypted(subjectId: Uuid, ciphertext: ByteArray?): String? {
            val decryptor = subjectKeys.decryptor(listOf(subjectId))
            return ciphertext?.let { (decryptor.decrypt(subjectId, it) as Decrypted.Plain).value.decodeToString() }
        }
    }

    @Test
    fun `should store the request's client snapshot and note under the client's key`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            fixture.setup()
            val request = fixture.request()

            whenn()
            val created = fixture.create(request)

            then()
            val row = fixture.storedRow(created.id)
            val clientId = request.client.id
            assertEquals(
                request.client.fullName,
                fixture.decrypted(clientId, row[AppointmentRequestTable.clientNameEncrypted]),
            )
            assertEquals(
                request.client.phone,
                fixture.decrypted(clientId, row[AppointmentRequestTable.clientPhoneEncrypted]),
            )
            assertEquals(
                request.client.email,
                fixture.decrypted(clientId, row[AppointmentRequestTable.clientEmailEncrypted]),
            )
            assertEquals("Short hair", fixture.decrypted(clientId, row[AppointmentRequestTable.noteEncrypted]))
            assertEquals(
                request.employee.fullName,
                fixture.decrypted(request.employee.userId, row[AppointmentRequestTable.employeeNameEncrypted]),
            )
        }

    @Test
    fun `should read back an encrypted request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.request())

        whenn()
        val read = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals(created, read)
    }

    @Test
    fun `should encrypt the changed snapshot on update`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.request())
        val employee = EmployeeSnapshot(id = Uuid.random(), userId = Uuid.random(), fullName = "Other Employee")

        whenn()
        suspendTransaction { fixture.sut.update(created.copy(employee = employee, note = "Long hair")) }

        then()
        val row = fixture.storedRow(created.id)
        assertEquals("Long hair", fixture.decrypted(created.client.id, row[AppointmentRequestTable.noteEncrypted]))
        assertEquals(
            "Other Employee",
            fixture.decrypted(employee.userId, row[AppointmentRequestTable.employeeNameEncrypted]),
        )
    }

    @Test
    fun `should read a declined request decrypted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.request())

        whenn()
        val declined = suspendTransaction { fixture.sut.decline(created.id, "Fully booked") }

        then()
        assertEquals(created.client, declined.client)
        assertEquals(created.note, declined.note)
    }

    @Test
    fun `should read an erased client's request as a placeholder`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.request())
        fixture.subjectKeys.shred(created.client.id)

        whenn()
        val read = suspendTransaction { fixture.sut.getPending(fixture.businessId) }.single()

        then()
        assertEquals(created.client.copy(fullName = "", phone = null, email = null, erased = true), read.client)
        assertEquals("", read.note)
    }

    @Test
    fun `should read a request whose key is missing as empty and report it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.request())
        fixture.subjectKeys.loseKey(created.client.id)

        whenn()
        val read = suspendTransaction { fixture.sut.getForUpdate(created.id) }

        then()
        assertEquals("", read?.client?.fullName)
        assertEquals(listOf("appointment_request"), fixture.missingKeys.reported)
    }

    @Test
    fun `should keep the stored client details an update over a lost key does not change`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.request())
        val before = fixture.storedRow(created.id)
        fixture.subjectKeys.loseKey(created.client.id)
        val unreadable = requireNotNull(suspendTransaction { fixture.sut.get(created.id) })

        whenn()
        suspendTransaction { fixture.sut.update(unreadable.copy(note = "Long hair")) }

        then()
        val after = fixture.storedRow(created.id)
        listOf(
            AppointmentRequestTable.clientNameEncrypted,
            AppointmentRequestTable.clientPhoneEncrypted,
            AppointmentRequestTable.clientEmailEncrypted,
        ).forEach { assertArrayEquals(before[it], after[it], it.name) }
        assertEquals("Long hair", fixture.decrypted(created.client.id, after[AppointmentRequestTable.noteEncrypted]))
    }

    @Test
    fun `should fetch the kept client's key once for every moved request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val keptId = Uuid.random()
        val merged = RequestClientSnapshot.stub()
        fixture.create(fixture.request(client = merged))
        fixture.create(fixture.request(client = merged))
        fixture.countingKeys.resetCounts()

        whenn()
        suspendTransaction { fixture.sut.reassignClients(fixture.businessId, listOf(merged.id), keptId) }

        then()
        assertEquals(listOf(keptId), fixture.countingKeys.encryptorsRequested)
    }

    @Test
    fun `should re-encrypt merged clients' requests under the kept client's key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val keptId = Uuid.random()
        val moved = fixture.create(fixture.request())

        whenn()
        suspendTransaction { fixture.sut.reassignClients(fixture.businessId, listOf(moved.client.id), keptId) }

        then()
        fixture.subjectKeys.shred(moved.client.id)
        val row = fixture.storedRow(moved.id)
        assertEquals(moved.client.fullName, fixture.decrypted(keptId, row[AppointmentRequestTable.clientNameEncrypted]))
        assertEquals(moved.client.copy(id = keptId), suspendTransaction { fixture.sut.get(moved.id) }?.client)
    }
}
