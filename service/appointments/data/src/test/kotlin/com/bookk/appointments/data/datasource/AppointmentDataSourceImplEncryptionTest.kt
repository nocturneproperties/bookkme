package com.bookk.appointments.data.datasource

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
import library.encryption.ofCaseInsensitive
import library.encryption.test.CountingSubjectKeys
import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import library.encryption.test.UnkeyedBlindIndex
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
        val countingKeys = CountingSubjectKeys(subjectKeys)
        val missingKeys = RecordingMissingKeys()
        val blindIndex = UnkeyedBlindIndex()
        val sut = AppointmentDataSourceImpl(SnapshotCipher(countingKeys, blindIndex, missingKeys))
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
            clientNote: String = "Please ring twice",
        ): Appointment {
            return Appointment.stub(businessId = businessId, date = date)
                .copy(client = client, employee = employee, note = note, clientNote = clientNote)
        }

        suspend fun create(appointment: Appointment): Appointment {
            return suspendTransaction { sut.create(appointment) }
        }

        suspend fun anonymizeEmployeeTheOldWay(id: Uuid) {
            suspendTransaction {
                AppointmentTable.update(where = { AppointmentTable.id eq id }) {
                    it[AppointmentTable.employeeNameEncrypted] = null
                    it[AppointmentTable.employeeUserId] = Uuid.NIL
                }
            }
        }

        suspend fun storedRow(id: Uuid): ResultRow {
            return suspendTransaction { AppointmentTable.selectAll().where { AppointmentTable.id eq id }.single() }
        }

        fun nameIndex(fullName: String): String {
            return blindIndex.ofCaseInsensitive(fullName)
        }

        suspend fun page(query: String, limit: Int = 10, offset: Long = 0): List<Uuid> {
            return suspendTransaction { sut.getAllPaginated(businessId, limit, offset, query) }.data.map { it.id }
        }

        suspend fun decrypted(subjectId: Uuid, ciphertext: ByteArray?): String? {
            val decryptor = subjectKeys.decryptor(listOf(subjectId))
            return ciphertext?.let { (decryptor.decrypt(subjectId, it) as Decrypted.Plain).value.decodeToString() }
        }
    }

    private fun assertKept(before: ResultRow, after: ResultRow, vararg columns: Column<out ByteArray?>) {
        columns.forEach { assertArrayEquals(before[it], after[it], it.name) }
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
            assertEquals("Alice Smith", fixture.decrypted(client.id, row[AppointmentTable.clientNameEncrypted]))
            assertEquals("+10000000000", fixture.decrypted(client.id, row[AppointmentTable.clientPhoneEncrypted]))
            assertEquals("alice@example.com", fixture.decrypted(client.id, row[AppointmentTable.clientEmailEncrypted]))
            assertEquals("Bring the red dress", fixture.decrypted(client.id, row[AppointmentTable.noteEncrypted]))
            assertEquals("Please ring twice", fixture.decrypted(client.id, row[AppointmentTable.clientNoteEncrypted]))
            assertEquals("Bob Stylist", fixture.decrypted(employee.userId, row[AppointmentTable.employeeNameEncrypted]))
        }

    @Test
    fun `should store a blind index of the client's full name`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val client = ClientSnapshot.stub().copy(fullName = "Alice Smith")

        whenn()
        val created = fixture.create(fixture.appointment(client = client))

        then()
        assertEquals(fixture.nameIndex("Alice Smith"), fixture.storedRow(created.id)[AppointmentTable.clientNameIndex])
    }

    @Test
    fun `should index the client name of an appointment created from a request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val request = AppointmentRequest.stub(businessId = fixture.businessId)

        whenn()
        val created = suspendTransaction { fixture.sut.create(request) }

        then()
        val stored = fixture.storedRow(created.id)[AppointmentTable.clientNameIndex]
        assertEquals(fixture.nameIndex(request.client.fullName), stored)
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
        assertEquals(
            request.client.fullName,
            fixture.decrypted(request.client.id, row[AppointmentTable.clientNameEncrypted]),
        )
        assertEquals(request.note, created.note)
        assertEquals(request.client.toClientSnapshot(), created.client)
    }

    @Test
    fun `should copy a request's note into the client's note of the appointment created from it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val request = AppointmentRequest.stub(businessId = fixture.businessId).copy(note = "Please ring twice")

        whenn()
        val created = suspendTransaction { fixture.sut.create(request) }

        then()
        val row = fixture.storedRow(created.id)
        assertEquals(
            "Please ring twice",
            fixture.decrypted(request.client.id, row[AppointmentTable.clientNoteEncrypted]),
        )
        assertEquals("Please ring twice", created.clientNote)
        assertEquals("Please ring twice", suspendTransaction { fixture.sut.get(created.id) }.clientNote)
    }

    @Test
    fun `should keep the stored client's note when the appointment's note changes`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())
        val before = fixture.storedRow(created.id)

        whenn()
        val updated = suspendTransaction { fixture.sut.update(created.copy(note = "Short hair")) }

        then()
        assertKept(before, fixture.storedRow(created.id), AppointmentTable.clientNoteEncrypted)
        assertEquals("Please ring twice", updated.clientNote)
    }

    @Test
    fun `should read an appointment stored without a client's note as one with an empty note`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())
        suspendTransaction {
            AppointmentTable.update(where = { AppointmentTable.id eq created.id }) {
                it[clientNoteEncrypted] = null
            }
        }

        whenn()
        val read = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals("", read.clientNote)
        assertEquals("Bring the red dress", read.note)
        assertEquals(emptyList<String>(), fixture.missingKeys.reported)
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
        assertEquals("Changed note", fixture.decrypted(created.client.id, row[AppointmentTable.noteEncrypted]))
        assertEquals("New Employee", fixture.decrypted(employee.userId, row[AppointmentTable.employeeNameEncrypted]))
        assertEquals(employee, updated.employee)
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
        assertEquals(created.client.copy(fullName = "", phone = null, email = null, erased = true), read.client)
        assertEquals("", read.note)
        assertEquals(created.employee.copy(fullName = "", erased = true), read.employee)
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
        assertEquals("", read.clientNote)
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
        assertNull(row[AppointmentTable.clientNameEncrypted])
        assertNull(row[AppointmentTable.noteEncrypted])
        assertNull(row[AppointmentTable.clientNameIndex])
        assertTrue(suspendTransaction { fixture.sut.get(created.id) }.client.erased)
    }

    @Test
    fun `should keep the stored client details an update over a lost key does not change`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())
        val before = fixture.storedRow(created.id)
        fixture.subjectKeys.loseKey(created.client.id)
        val unreadable = suspendTransaction { fixture.sut.get(created.id) }

        whenn()
        suspendTransaction { fixture.sut.update(unreadable.copy(note = "Short hair")) }

        then()
        val after = fixture.storedRow(created.id)
        assertKept(before, after, AppointmentTable.clientNameEncrypted, AppointmentTable.clientPhoneEncrypted)
        assertKept(before, after, AppointmentTable.clientEmailEncrypted)
        assertEquals(before[AppointmentTable.clientNameIndex], after[AppointmentTable.clientNameIndex])
        assertEquals("Short hair", fixture.decrypted(created.client.id, after[AppointmentTable.noteEncrypted]))
    }

    @Test
    fun `should read the re-entered note of an appointment whose client details stay unreadable`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())
        fixture.subjectKeys.loseKey(created.client.id)
        val unreadable = suspendTransaction { fixture.sut.get(created.id) }
        suspendTransaction { fixture.sut.update(unreadable.copy(note = "Short hair")) }
        val reportedBefore = fixture.missingKeys.reported.size

        whenn()
        val read = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals(created.client.copy(fullName = "", phone = null, email = null), read.client)
        assertEquals("Short hair", read.note)
        assertEquals(listOf("appointment"), fixture.missingKeys.reported.drop(reportedBefore))
    }

    @Test
    fun `should keep the stored employee name an update over a lost key does not change`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())
        val before = fixture.storedRow(created.id)
        fixture.subjectKeys.loseKey(created.employee.userId)
        val unreadable = suspendTransaction { fixture.sut.get(created.id) }

        whenn()
        suspendTransaction { fixture.sut.update(unreadable.copy(date = Instant.fromEpochMilliseconds(3_600_000))) }

        then()
        assertKept(before, fixture.storedRow(created.id), AppointmentTable.employeeNameEncrypted)
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
        assertNull(row[AppointmentTable.clientNoteEncrypted])
        assertNull(row[AppointmentTable.clientNameIndex])
        val read = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(ClientSnapshot(created.client.id, "", null, null, null, erased = true), read.client)
        assertEquals("", read.note)
        assertEquals("", read.clientNote)
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
        assertNull(row[AppointmentTable.clientNoteEncrypted])
        assertNull(row[AppointmentTable.clientNameIndex])
        val read = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(ClientSnapshot(created.client.id, "", null, null, null, erased = true), read.client)
    }

    @Test
    fun `should read an employee erased before shredding replaced anonymization as a deleted user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())
        fixture.anonymizeEmployeeTheOldWay(created.id)

        whenn()
        val read = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals(EmployeeSnapshot(created.employee.id, Uuid.NIL, "", erased = true), read.employee)
        assertEquals(emptyList<String>(), fixture.missingKeys.reported)
    }

    @Test
    fun `should find appointments by the client's full name typed in another case`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val match = fixture.create(fixture.appointment(client = ClientSnapshot.stub().copy(fullName = "Alice Smith")))
        fixture.create(fixture.appointment(client = ClientSnapshot.stub().copy(fullName = "Carol Jones")))

        whenn()
        val page = suspendTransaction { fixture.sut.getAllPaginated(fixture.businessId, 10, 0, " aLiCe sMiTh ") }

        then()
        assertEquals(listOf(match.id), page.data.map { it.id })
        assertEquals(1L, page.metadata.total)
    }

    @Test
    fun `should not find appointments by part of the client's name`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        fixture.create(fixture.appointment(client = ClientSnapshot.stub().copy(fullName = "Alice Smith")))

        whenn()
        val found = fixture.page("alice")

        then()
        assertEquals(emptyList<Uuid>(), found)
    }

    @Test
    fun `should not find another business's appointments by the client's name`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        fixture.create(fixture.appointment(client = ClientSnapshot.stub().copy(fullName = "Alice Smith")))
        fixture.setup()

        whenn()
        val found = fixture.page("alice smith")

        then()
        assertEquals(emptyList<Uuid>(), found)
    }

    @Test
    fun `should find appointments by the client's full name or part of a service name`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val byName = fixture.create(fixture.appointment(client = ClientSnapshot.stub().copy(fullName = "Alice Smith")))
        val byService = fixture.create(
            fixture.appointment(date = Instant.fromEpochMilliseconds(3_600_000)).copy(
                services = listOf(ServiceSnapshot.stub().copy(name = "Alice Smith's haircut")),
            ),
        )
        fixture.create(fixture.appointment(client = ClientSnapshot.stub().copy(fullName = "Carol Jones")))

        whenn()
        val found = fixture.page("alice smith")

        then()
        assertEquals(listOf(byService.id, byName.id), found)
    }

    @Test
    fun `should page client name matches newest first`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val matches = (0 until 3).map { hour ->
            fixture.create(
                fixture.appointment(
                    client = ClientSnapshot.stub().copy(fullName = "Alice Smith"),
                    date = Instant.fromEpochMilliseconds(hour * 3_600_000L),
                ),
            )
        }

        whenn()
        val page = suspendTransaction { fixture.sut.getAllPaginated(fixture.businessId, 2, 2, "alice smith") }

        then()
        assertEquals(listOf(matches[0].id), page.data.map { it.id })
        assertEquals(3L, page.metadata.total)
        assertEquals(2L, page.metadata.page)
    }

    @Test
    fun `should find an appointment by the client's new name after an update`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment(client = ClientSnapshot.stub().copy(fullName = "Alice Smith")))

        whenn()
        suspendTransaction { fixture.sut.update(created.copy(client = created.client.copy(fullName = "Alice Jones"))) }

        then()
        assertEquals(listOf(created.id), fixture.page("alice jones"))
        assertEquals(emptyList<Uuid>(), fixture.page("alice smith"))
    }

    @Test
    fun `should not decrypt appointments that don't match the search`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val unreadable = fixture.create(
            fixture.appointment(client = ClientSnapshot.stub().copy(fullName = "Carol Jones")),
        )
        fixture.subjectKeys.loseKey(unreadable.client.id)
        fixture.create(fixture.appointment(client = ClientSnapshot.stub().copy(fullName = "Alice Smith")))

        whenn()
        fixture.page("alice smith")

        then()
        assertEquals(emptyList<String>(), fixture.missingKeys.reported)
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
        assertEquals("Please ring twice", fixture.decrypted(kept.id, row[AppointmentTable.clientNoteEncrypted]))
        assertEquals(fixture.nameIndex("Merged Name"), row[AppointmentTable.clientNameIndex])
        val read = suspendTransaction { fixture.sut.get(moved.id) }
        assertEquals(merged.copy(id = kept.id), read.client)
    }

    @Test
    fun `should fetch the kept client's key once for every moved appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val kept = ClientSnapshot.stub()
        val merged = ClientSnapshot.stub()
        fixture.create(fixture.appointment(client = merged))
        fixture.create(fixture.appointment(client = merged, date = Instant.fromEpochMilliseconds(3_600_000)))
        fixture.countingKeys.resetCounts()

        whenn()
        suspendTransaction { fixture.sut.reassignClients(fixture.businessId, listOf(merged.id), kept.id) }

        then()
        assertEquals(listOf(kept.id), fixture.countingKeys.encryptorsRequested)
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
    fun `should keep a deleted employee erased when the appointment is updated`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())
        fixture.subjectKeys.shred(created.employee.userId)
        val erased = suspendTransaction { fixture.sut.get(created.id) }

        whenn()
        suspendTransaction { fixture.sut.update(erased.copy(note = "Changed")) }

        then()
        val read = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(created.employee.copy(fullName = "", erased = true), read.employee)
        assertEquals("Changed", read.note)
    }

    @Test
    fun `should not store the name of an employee erased before shredding replaced anonymization`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(fixture.appointment())
        fixture.anonymizeEmployeeTheOldWay(created.id)
        val anonymized = suspendTransaction { fixture.sut.get(created.id) }

        whenn()
        suspendTransaction { fixture.sut.update(anonymized.copy(note = "Changed")) }

        then()
        val row = fixture.storedRow(created.id)
        assertNull(row[AppointmentTable.employeeNameEncrypted])
        assertTrue(suspendTransaction { fixture.sut.get(created.id) }.employee.erased)
    }
}
