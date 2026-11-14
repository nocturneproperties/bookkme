package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.entity.AppointmentServiceEntity
import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.AppointmentServicesTable
import com.bookk.appointments.data.orm.table.AppointmentTable
import com.bookk.appointments.data.orm.table.DayOffsTable
import com.bookk.appointments.data.orm.table.PriceAdjustmentServicesTable
import com.bookk.appointments.data.orm.table.PriceAdjustmentTable
import com.bookk.appointments.data.orm.table.SettingsTable
import com.bookk.appointments.data.orm.table.WorkingHoursTable
import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.entity.ClientSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.encryption.Decrypted
import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AppointmentDataSourceImplEncryptionTest {

    private class SutFixture {
        val db = createTestDatabase(
            AppointmentBusinessTable,
            WorkingHoursTable,
            DayOffsTable,
            SettingsTable,
            AppointmentTable,
            AppointmentServicesTable,
            PriceAdjustmentTable,
            PriceAdjustmentServicesTable,
        )
        val subjectKeys = InMemorySubjectKeys()
        val missingKeys = RecordingMissingKeys()
        val sut = AppointmentDataSourceImpl(SnapshotCipher(subjectKeys, missingKeys))
        lateinit var businessId: Uuid

        suspend fun setup() {
            val snapshot = BusinessSnapshot.stub()
            suspendTransaction {
                AppointmentSubscriptionDataSourceImpl().attachBusiness(snapshot)
                AppointmentSettingsDataSourceImpl().create(AppointmentSettings.stub(snapshot.id))
            }
            businessId = snapshot.id
        }

        fun appointment(
            client: ClientSnapshot = ClientSnapshot.stub(),
            employee: EmployeeSnapshot = EmployeeSnapshot.stub(),
            note: String = "Bring the red dress",
            date: Instant = Instant.fromEpochMilliseconds(0),
        ): Appointment {
            return Appointment.stub(businessId = businessId, date = date)
                .copy(client = client, employee = employee, note = note)
        }

        suspend fun create(appointment: Appointment): Appointment {
            return suspendTransaction { sut.create(appointment) }
        }

        suspend fun storedRow(id: Uuid): ResultRow {
            return suspendTransaction { AppointmentTable.selectAll().where { AppointmentTable.id eq id }.single() }
        }

        suspend fun insertLegacy(appointment: Appointment) {
            suspendTransaction {
                AppointmentTable.insert {
                    it[AppointmentTable.id] = appointment.id
                    it[initiatorUserId] = appointment.initiatorUserId
                    it[businessId] = appointment.businessId
                    it[employeeId] = appointment.employee.id
                    it[employeeUserId] = appointment.employee.userId
                    it[employeeName] = appointment.employee.fullName
                    it[clientId] = appointment.client.id
                    it[clientUserId] = appointment.client.userId
                    it[clientName] = appointment.client.fullName
                    it[clientPhone] = appointment.client.phone
                    it[clientEmail] = appointment.client.email
                    it[dateStart] = appointment.date
                    it[dateEnd] = appointment.dateEnd
                    it[note] = appointment.note
                    it[status] = AppointmentStatus.SCHEDULED
                    it[cancellationReason] = ""
                }
                appointment.services.forEach {
                    AppointmentServiceEntity.new(EntityID(appointment.id, AppointmentTable), it)
                }
            }
        }

        suspend fun decrypted(subjectId: Uuid, ciphertext: ByteArray?): String? {
            val decryptor = subjectKeys.decryptor(listOf(subjectId))
            return ciphertext?.let { (decryptor.decrypt(subjectId, it) as Decrypted.Plain).value.decodeToString() }
        }
    }

    @Test
    fun `should store the client snapshot and note under the client's key and the employee under theirs`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            fixture.setup()
            val client = ClientSnapshot(
                id = Uuid.random(),
                fullName = "Alice Smith",
                phone = "+10000000000",
                email = "alice@example.com",
                userId = Uuid.random(),
            )
            val employee = EmployeeSnapshot(id = Uuid.random(), userId = Uuid.random(), fullName = "Bob Stylist")

            whenn()
            val created = fixture.create(fixture.appointment(client = client, employee = employee))

            then()
            val row = fixture.storedRow(created.id)
            assertNull(row[AppointmentTable.clientName])
            assertNull(row[AppointmentTable.clientPhone])
            assertNull(row[AppointmentTable.clientEmail])
            assertNull(row[AppointmentTable.note])
            assertNull(row[AppointmentTable.employeeName])
            assertEquals("Alice Smith", fixture.decrypted(client.id, row[AppointmentTable.clientNameEncrypted]))
            assertEquals("+10000000000", fixture.decrypted(client.id, row[AppointmentTable.clientPhoneEncrypted]))
            assertEquals("alice@example.com", fixture.decrypted(client.id, row[AppointmentTable.clientEmailEncrypted]))
            assertEquals("Bring the red dress", fixture.decrypted(client.id, row[AppointmentTable.noteEncrypted]))
            assertEquals("Bob Stylist", fixture.decrypted(employee.userId, row[AppointmentTable.employeeNameEncrypted]))
        }

    @Test
    fun `should store an appointment created from a request encrypted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val request = AppointmentRequest.stub(businessId = fixture.businessId)

        whenn()
        val created = suspendTransaction { fixture.sut.create(request) }

        then()
        val row = fixture.storedRow(created.id)
        assertNull(row[AppointmentTable.clientName])
        assertEquals(
            request.client.fullName,
            fixture.decrypted(request.client.id, row[AppointmentTable.clientNameEncrypted]),
        )
        assertEquals(request.note, created.note)
        assertEquals(request.client.toClientSnapshot(), created.client)
    }

    @Test
    fun `should read back an encrypted appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())

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
        val created = fixture.create(fixture.appointment())
        val employee = EmployeeSnapshot(id = Uuid.random(), userId = Uuid.random(), fullName = "New Employee")

        whenn()
        val updated = suspendTransaction {
            fixture.sut.update(created.copy(employee = employee, note = "Changed note"))
        }

        then()
        val row = fixture.storedRow(created.id)
        assertNull(row[AppointmentTable.note])
        assertNull(row[AppointmentTable.employeeName])
        assertEquals("Changed note", fixture.decrypted(created.client.id, row[AppointmentTable.noteEncrypted]))
        assertEquals("New Employee", fixture.decrypted(employee.userId, row[AppointmentTable.employeeNameEncrypted]))
        assertEquals(employee, updated.employee)
    }

    @Test
    fun `should read a plaintext appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val legacy = fixture.appointment()
        fixture.insertLegacy(legacy)

        whenn()
        val read = suspendTransaction { fixture.sut.get(legacy.id) }

        then()
        assertEquals(legacy.client, read.client)
        assertEquals(legacy.employee, read.employee)
        assertEquals(legacy.note, read.note)
    }

    @Test
    fun `should read an erased client and employee as placeholders`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())
        fixture.subjectKeys.shredAll(listOf(created.client.id, created.employee.userId))

        whenn()
        val read = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals(created.client.copy(fullName = "Deleted Client", phone = null, email = null), read.client)
        assertEquals("", read.note)
        assertEquals(created.employee.copy(fullName = "Deleted User"), read.employee)
        assertEquals(emptyList<String>(), fixture.missingKeys.reported)
    }

    @Test
    fun `should read an appointment whose keys are missing as empty and report it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())
        fixture.subjectKeys.loseKey(created.client.id)
        fixture.subjectKeys.loseKey(created.employee.userId)

        whenn()
        val read = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals(created.client.copy(fullName = "", phone = null, email = null), read.client)
        assertEquals("", read.note)
        assertEquals(created.employee.copy(fullName = ""), read.employee)
        assertEquals(listOf("appointment", "appointment"), fixture.missingKeys.reported)
    }

    @Test
    fun `should keep an erased client's appointment unreadable when it is updated`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())
        fixture.subjectKeys.shred(created.client.id)
        val erased = suspendTransaction { fixture.sut.get(created.id) }

        whenn()
        suspendTransaction { fixture.sut.update(erased.copy(date = Instant.fromEpochMilliseconds(3_600_000))) }

        then()
        val row = fixture.storedRow(created.id)
        assertNull(row[AppointmentTable.clientName])
        assertNull(row[AppointmentTable.clientNameEncrypted])
        assertNull(row[AppointmentTable.noteEncrypted])
        assertEquals("Deleted Client", suspendTransaction { fixture.sut.get(created.id) }.client.fullName)
    }

    @Test
    fun `should erase an anonymized client's encrypted details`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())

        whenn()
        suspendTransaction { fixture.sut.anonymizeClient(fixture.businessId, created.client.id) }

        then()
        val row = fixture.storedRow(created.id)
        assertNull(row[AppointmentTable.clientNameEncrypted])
        assertNull(row[AppointmentTable.clientPhoneEncrypted])
        assertNull(row[AppointmentTable.clientEmailEncrypted])
        assertNull(row[AppointmentTable.noteEncrypted])
        assertNull(row[AppointmentTable.clientName])
        assertNull(row[AppointmentTable.note])
        val read = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(ClientSnapshot(created.client.id, "Deleted Client", null, null, null), read.client)
        assertEquals("", read.note)
    }

    @Test
    fun `should erase a deleted account's encrypted client details`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())

        whenn()
        suspendTransaction { fixture.sut.anonymizeClientUser(requireNotNull(created.client.userId)) }

        then()
        val row = fixture.storedRow(created.id)
        assertNull(row[AppointmentTable.clientNameEncrypted])
        assertNull(row[AppointmentTable.noteEncrypted])
        assertNull(row[AppointmentTable.clientName])
        val read = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(ClientSnapshot(created.client.id, "Deleted Client", null, null, null), read.client)
    }

    @Test
    fun `should erase a deleted employee's encrypted name`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())

        whenn()
        suspendTransaction { fixture.sut.anonymizeEmployee(created.employee.userId) }

        then()
        val row = fixture.storedRow(created.id)
        assertNull(row[AppointmentTable.employeeNameEncrypted])
        assertNull(row[AppointmentTable.employeeName])
        val read = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(EmployeeSnapshot(created.employee.id, Uuid.NIL, "Deleted User"), read.employee)
    }

    @Test
    fun `should find appointments by an encrypted client name typed in another case`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val match = fixture.create(fixture.appointment(client = ClientSnapshot.stub().copy(fullName = "Alice Smith")))
        fixture.create(fixture.appointment(client = ClientSnapshot.stub().copy(fullName = "Carol Jones")))

        whenn()
        val page = suspendTransaction { fixture.sut.getAllPaginated(fixture.businessId, 10, 0, "aLiCe") }

        then()
        assertEquals(listOf(match.id), page.data.map { it.id })
        assertEquals(1L, page.metadata.total)
    }

    @Test
    fun `should find appointments by a plaintext client name or a service name`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val legacy = fixture.appointment(client = ClientSnapshot.stub().copy(fullName = "Alice Legacy"))
        fixture.insertLegacy(legacy)
        val byService = fixture.create(
            fixture.appointment(date = Instant.fromEpochMilliseconds(3_600_000)).copy(
                services = listOf(ServiceSnapshot.stub().copy(name = "Alice's haircut")),
            ),
        )
        fixture.create(fixture.appointment(client = ClientSnapshot.stub().copy(fullName = "Carol Jones")))

        whenn()
        val page = suspendTransaction { fixture.sut.getAllPaginated(fixture.businessId, 10, 0, "alice") }

        then()
        assertEquals(listOf(byService.id, legacy.id), page.data.map { it.id })
    }

    @Test
    fun `should page client name matches newest first`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val matches = (0 until 3).map { hour ->
            fixture.create(
                fixture.appointment(
                    client = ClientSnapshot.stub().copy(fullName = "Alice $hour"),
                    date = Instant.fromEpochMilliseconds(hour * 3_600_000L),
                ),
            )
        }

        whenn()
        val page = suspendTransaction { fixture.sut.getAllPaginated(fixture.businessId, 2, 2, "alice") }

        then()
        assertEquals(listOf(matches[0].id), page.data.map { it.id })
        assertEquals(3L, page.metadata.total)
        assertEquals(2L, page.metadata.page)
    }

    @Test
    fun `should re-encrypt merged clients' appointments under the kept client's key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val kept = ClientSnapshot.stub()
        val merged = ClientSnapshot.stub().copy(fullName = "Merged Name")
        val moved = fixture.create(fixture.appointment(client = merged, note = "Merged note"))

        whenn()
        suspendTransaction { fixture.sut.reassignClients(fixture.businessId, listOf(merged.id), kept.id) }

        then()
        fixture.subjectKeys.shred(merged.id)
        val row = fixture.storedRow(moved.id)
        assertEquals("Merged Name", fixture.decrypted(kept.id, row[AppointmentTable.clientNameEncrypted]))
        assertEquals("Merged note", fixture.decrypted(kept.id, row[AppointmentTable.noteEncrypted]))
        val read = suspendTransaction { fixture.sut.get(moved.id) }
        assertEquals(merged.copy(id = kept.id), read.client)
    }

    @Test
    fun `should link the account on merged appointments moved in the same transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val keptId = Uuid.random()
        val userId = Uuid.random()
        val moved = fixture.create(fixture.appointment(client = ClientSnapshot.stub(userId = null)))

        whenn()
        suspendTransaction {
            fixture.sut.reassignClients(fixture.businessId, listOf(moved.client.id), keptId)
            fixture.sut.linkClientUser(fixture.businessId, keptId, userId)
        }

        then()
        val read = suspendTransaction { fixture.sut.get(moved.id) }
        assertEquals(moved.client.copy(id = keptId, userId = userId), read.client)
        assertEquals(moved.note, read.note)
    }

    @Test
    fun `should move a plaintext appointment of a merged client encrypted under the kept client's key`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            fixture.setup()
            val kept = ClientSnapshot.stub()
            val legacy = fixture.appointment()
            fixture.insertLegacy(legacy)

            whenn()
            suspendTransaction { fixture.sut.reassignClients(fixture.businessId, listOf(legacy.client.id), kept.id) }

            then()
            val row = fixture.storedRow(legacy.id)
            assertNull(row[AppointmentTable.clientName])
            assertEquals(legacy.client.fullName, fixture.decrypted(kept.id, row[AppointmentTable.clientNameEncrypted]))
        }

    @Test
    fun `should encrypt plaintext appointments and clear their plaintext`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val legacy = fixture.appointment()
        fixture.insertLegacy(legacy)

        whenn()
        val encrypted = suspendTransaction { fixture.sut.encryptPlaintextAppointments(limit = 10) }

        then()
        assertEquals(1, encrypted)
        val row = fixture.storedRow(legacy.id)
        assertNull(row[AppointmentTable.clientName])
        assertNull(row[AppointmentTable.clientPhone])
        assertNull(row[AppointmentTable.clientEmail])
        assertNull(row[AppointmentTable.note])
        assertNull(row[AppointmentTable.employeeName])
        assertEquals(
            legacy.client.email,
            fixture.decrypted(legacy.client.id, row[AppointmentTable.clientEmailEncrypted]),
        )
        assertEquals(legacy.note, fixture.decrypted(legacy.client.id, row[AppointmentTable.noteEncrypted]))
        assertEquals(
            legacy.employee.fullName,
            fixture.decrypted(legacy.employee.userId, row[AppointmentTable.employeeNameEncrypted]),
        )
        assertEquals(legacy.client, suspendTransaction { fixture.sut.get(legacy.id) }.client)
    }

    @Test
    fun `should encrypt at most the given number of plaintext appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        repeat(3) { fixture.insertLegacy(fixture.appointment()) }

        whenn()
        val first = suspendTransaction { fixture.sut.encryptPlaintextAppointments(limit = 2) }
        val second = suspendTransaction { fixture.sut.encryptPlaintextAppointments(limit = 2) }
        val third = suspendTransaction { fixture.sut.encryptPlaintextAppointments(limit = 2) }

        then()
        assertEquals(listOf(2, 1, 0), listOf(first, second, third))
    }

    @Test
    fun `should drop the plaintext of an erased person instead of encrypting it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val legacy = fixture.appointment(employee = EmployeeSnapshot.stub(userId = Uuid.NIL))
        fixture.insertLegacy(legacy)
        fixture.subjectKeys.shred(legacy.client.id)

        whenn()
        val encrypted = suspendTransaction { fixture.sut.encryptPlaintextAppointments(limit = 10) }

        then()
        assertEquals(1, encrypted)
        val row = fixture.storedRow(legacy.id)
        assertNull(row[AppointmentTable.clientName])
        assertNull(row[AppointmentTable.clientNameEncrypted])
        assertNull(row[AppointmentTable.employeeName])
        assertNull(row[AppointmentTable.employeeNameEncrypted])
        val read = suspendTransaction { fixture.sut.get(legacy.id) }
        assertEquals("Deleted Client", read.client.fullName)
        assertEquals("Deleted User", read.employee.fullName)
    }

    @Test
    fun `should not store a deleted employee's name under a key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())
        suspendTransaction { fixture.sut.anonymizeEmployee(created.employee.userId) }
        val anonymized = suspendTransaction { fixture.sut.get(created.id) }

        whenn()
        suspendTransaction { fixture.sut.update(anonymized.copy(note = "Changed")) }

        then()
        val row = fixture.storedRow(created.id)
        assertNull(row[AppointmentTable.employeeNameEncrypted])
        assertNull(row[AppointmentTable.employeeName])
        assertEquals("Deleted User", suspendTransaction { fixture.sut.get(created.id) }.employee.fullName)
    }
}
