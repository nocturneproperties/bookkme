package com.bookk.business.data.datasource

import com.bookk.business.data.orm.table.BusinessDashboardTable
import com.bookk.business.data.orm.table.BusinessDayOffTable
import com.bookk.business.data.orm.table.BusinessPermissionGrantsTable
import com.bookk.business.data.orm.table.BusinessTable
import com.bookk.business.data.orm.table.BusinessWorkingHoursTable
import com.bookk.business.data.orm.table.ClientTable
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientMerge
import com.bookk.business.domain.api.client.entity.ClientUpdateModel
import com.bookk.core.data.map.toDomain
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.PageRequest
import com.bookk.core.domain.entity.pageRequest
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.TimeZone
import library.encryption.Decrypted
import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import library.encryption.test.UnkeyedBlindIndex
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ClientDataSourceImplEncryptionTest {

    private class SutFixture {
        val db = createTestDatabase(
            BusinessTable,
            BusinessDashboardTable,
            BusinessPermissionGrantsTable,
            BusinessWorkingHoursTable,
            BusinessDayOffTable,
            ClientTable,
        )
        val subjectKeys = InMemorySubjectKeys()
        val blindIndex = UnkeyedBlindIndex()
        val missingKeys = RecordingMissingKeys()
        val sut = ClientDataSourceImpl(ContactCipher(subjectKeys, blindIndex, missingKeys))
        val businessSut = BusinessDataSourceImpl()
        lateinit var businessId: Uuid

        suspend fun clients(): List<Client> {
            return suspendTransaction { sut.getClientsPage(businessId, pageRequest(PageRequest.MAX_LIMIT), null) }.items
        }

        suspend fun setup() {
            businessId = suspendTransaction {
                businessSut.createBusiness(Uuid.random(), "Test Business", "USD", TimeZone.UTC)
            }.id
        }

        suspend fun storedRow(id: Uuid): ResultRow {
            return suspendTransaction { ClientTable.selectAll().where { ClientTable.id eq id }.single() }
        }

        suspend fun decrypted(id: Uuid, ciphertext: ByteArray?): String? {
            val decryptor = subjectKeys.decryptor(listOf(id))
            return ciphertext?.let { (decryptor.decrypt(id, it) as Decrypted.Plain).value.decodeToString() }
        }
    }

    private fun nameEdit(id: Uuid, name: String): ClientUpdateModel {
        return ClientUpdateModel(id = id, name = name, lastName = null, phone = null, email = null, description = null)
    }

    private fun fullClient(): Client.Detached {
        return Client.Detached.stub(
            name = "Alice",
            lastName = "Smith",
            phone = "+10000000001",
            email = "alice@example.com",
            description = "note",
        )
    }

    private fun assertKept(before: ResultRow, after: ResultRow, vararg columns: Column<out ByteArray?>) {
        columns.forEach { assertArrayEquals(before[it], after[it], it.name) }
    }

    private fun emailEdit(id: Uuid, email: String): ClientUpdateModel {
        return ClientUpdateModel(id = id, name = null, lastName = null, phone = null, email = email, description = null)
    }

    @Test
    fun `should store a client encrypted under the client's key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val client = Client.Detached.stub(
            name = "Alice",
            lastName = "Smith",
            phone = "+10000000000",
            email = "alice@example.com",
            description = "Prefers mornings",
        )

        whenn()
        val created = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, client) }

        then()
        val row = fixture.storedRow(created.id)
        assertEquals("Alice", fixture.decrypted(created.id, row[ClientTable.nameEncrypted]))
        assertEquals("Smith", fixture.decrypted(created.id, row[ClientTable.lastNameEncrypted]))
        assertEquals("+10000000000", fixture.decrypted(created.id, row[ClientTable.phoneEncrypted]))
        assertEquals("alice@example.com", fixture.decrypted(created.id, row[ClientTable.emailEncrypted]))
        assertEquals("Prefers mornings", fixture.decrypted(created.id, row[ClientTable.descriptionEncrypted]))
        assertEquals(fixture.blindIndex.of("alice@example.com"), row[ClientTable.emailIndex])
    }

    @Test
    fun `should read back an encrypted client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(description = "note"))
        }

        whenn()
        val read = suspendTransaction { fixture.sut.getClientById(fixture.businessId, created.id) }

        then()
        assertEquals(created, read)
    }

    @Test
    fun `should store each imported client under its own key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clients = listOf(
            Client.Detached.stub(name = "First", email = "first@example.com"),
            Client.Detached.stub(name = "Second", email = "second@example.com"),
        )

        whenn()
        val created = suspendTransaction { fixture.sut.createDetachedClients(fixture.businessId, clients) }

        then()
        created.forEach { client ->
            val row = fixture.storedRow(client.id)
            assertEquals(client.name, fixture.decrypted(client.id, row[ClientTable.nameEncrypted]))
        }
    }

    @Test
    fun `should find clients by email typed in another case`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(email = "find-me@example.com"))
        }

        whenn()
        val found = suspendTransaction {
            fixture.sut.getClientsByEmails(fixture.businessId, listOf(" Find-Me@Example.COM"))
        }

        then()
        assertEquals(listOf(created), found)
    }

    @Test
    fun `should reject a client whose email differs from another client's only by case`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction {
            fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(email = "taken@example.com"))
        }

        whenn()
        val result = runCatching {
            suspendTransaction {
                fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(email = "Taken@Example.com"))
            }
        }

        then()
        assertTrue(result.exceptionOrNull()?.toDomain() is Error.UniqueConstraintFailed) { result.toString() }
    }

    @Test
    fun `should allow the same email in another business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        suspendTransaction {
            fixture.sut.createDetachedClient(otherBusinessId, Client.Detached.stub(email = "shared@example.com"))
        }

        whenn()
        val created = suspendTransaction {
            fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(email = "shared@example.com"))
        }

        then()
        assertEquals("shared@example.com", created.email)
    }

    @Test
    fun `should keep the email index of a client whose key is missing when the email is not edited`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(email = "kept@example.com"))
        }
        fixture.subjectKeys.loseKey(created.id)

        whenn()
        suspendTransaction {
            fixture.sut.updateClient(
                fixture.businessId,
                ClientUpdateModel(
                    id = created.id,
                    name = "Again",
                    lastName = null,
                    phone = null,
                    email = null,
                    description = null,
                ),
            )
        }

        then()
        assertEquals(fixture.blindIndex.of("kept@example.com"), fixture.storedRow(created.id)[ClientTable.emailIndex])
    }

    @Test
    fun `should keep the stored fields an edit over a lost key does not change`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createDetachedClient(fixture.businessId, fullClient())
        }
        val before = fixture.storedRow(created.id)
        fixture.subjectKeys.loseKey(created.id)

        whenn()
        suspendTransaction { fixture.sut.updateClient(fixture.businessId, nameEdit(created.id, "Again")) }

        then()
        val after = fixture.storedRow(created.id)
        assertEquals("Again", fixture.decrypted(created.id, after[ClientTable.nameEncrypted]))
        assertKept(before, after, ClientTable.lastNameEncrypted, ClientTable.phoneEncrypted)
        assertKept(before, after, ClientTable.emailEncrypted, ClientTable.descriptionEncrypted)
    }

    @Test
    fun `should read the re-entered field of a client whose other fields stay unreadable`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, fullClient()) }
        fixture.subjectKeys.loseKey(created.id)
        suspendTransaction { fixture.sut.updateClient(fixture.businessId, nameEdit(created.id, "Again")) }
        val reportedBefore = fixture.missingKeys.reported.size

        whenn()
        val read = suspendTransaction { fixture.sut.getClientById(fixture.businessId, created.id) }

        then()
        assertEquals(
            Client.Detached(id = created.id, name = "Again", lastName = "", phone = null, email = null),
            read,
        )
        assertEquals(listOf("client"), fixture.missingKeys.reported.drop(reportedBefore))
    }

    @Test
    fun `should keep the stored description of a client taken over after its key was lost`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val detached = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, fullClient()) }
        val before = fixture.storedRow(detached.id)
        fixture.subjectKeys.loseKey(detached.id)
        val profile = Client.Integrated.stub(name = "Guest", email = null, phone = null, userId = Uuid.random())

        whenn()
        suspendTransaction { fixture.sut.takeOverClient(fixture.businessId, detached.id, profile) }

        then()
        val after = fixture.storedRow(detached.id)
        assertEquals("Guest", fixture.decrypted(detached.id, after[ClientTable.nameEncrypted]))
        assertKept(before, after, ClientTable.phoneEncrypted, ClientTable.emailEncrypted)
        assertKept(before, after, ClientTable.descriptionEncrypted)
    }

    @Test
    fun `should keep the stored phone and description of a client synced after its key was lost`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val created = suspendTransaction {
            fixture.sut.getOrCreateIntegratedClient(
                fixture.businessId,
                Client.Integrated.stub(phone = "+10000000001", userId = userId),
            )
        }
        val before = fixture.storedRow(created.id)
        fixture.subjectKeys.loseKey(created.id)

        whenn()
        suspendTransaction {
            fixture.sut.updateIntegratedClients(
                userId,
                name = "New",
                lastName = "Name",
                email = "new@example.com",
                phone = null,
                updatedAt = Instant.fromEpochSeconds(1_000),
            )
        }

        then()
        val after = fixture.storedRow(created.id)
        assertEquals("new@example.com", fixture.decrypted(created.id, after[ClientTable.emailEncrypted]))
        assertKept(before, after, ClientTable.phoneEncrypted, ClientTable.descriptionEncrypted)
    }

    @Test
    fun `should read a client whose key is missing as empty fields and report it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(description = "note"))
        }
        fixture.subjectKeys.loseKey(created.id)

        whenn()
        val read = suspendTransaction { fixture.sut.getClientById(fixture.businessId, created.id) }

        then()
        assertEquals(
            Client.Detached(id = created.id, name = "", lastName = "", phone = null, email = null, description = null),
            read,
        )
        assertEquals(listOf("client"), fixture.missingKeys.reported)
    }

    @Test
    fun `should read a client whose key is shredded as empty fields without a report`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub())
        }
        fixture.subjectKeys.shred(created.id)

        whenn()
        val read = fixture.clients()

        then()
        assertEquals(
            listOf(Client.Detached(id = created.id, name = "", lastName = "", phone = null, email = null)),
            read,
        )
        assertTrue(fixture.missingKeys.reported.isEmpty())
    }

    @Test
    fun `should encrypt an integrated client under the client's key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()

        whenn()
        val created = suspendTransaction {
            fixture.sut.getOrCreateIntegratedClient(
                fixture.businessId,
                Client.Integrated.stub(name = "Bob", email = "bob@example.com", userId = userId),
            )
        }

        then()
        val row = fixture.storedRow(created.id)
        assertEquals("Bob", fixture.decrypted(created.id, row[ClientTable.nameEncrypted]))
        assertEquals(fixture.blindIndex.of("bob@example.com"), row[ClientTable.emailIndex])
        assertEquals(created, suspendTransaction { fixture.sut.getClientByUserId(fixture.businessId, userId) })
    }

    @Test
    fun `should encrypt a taken over client with the profile's details`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val legacy = Client.Detached.stub(name = "Placeholder", email = "guest@example.com", description = "note")
        val detached = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, legacy) }
        val profile = Client.Integrated.stub(name = "Guest", email = "guest@example.com", userId = Uuid.random())

        whenn()
        val takenOver = suspendTransaction { fixture.sut.takeOverClient(fixture.businessId, detached.id, profile) }

        then()
        val row = fixture.storedRow(detached.id)
        assertEquals("Guest", takenOver?.name)
        assertEquals("note", takenOver?.description)
        assertEquals("Guest", fixture.decrypted(detached.id, row[ClientTable.nameEncrypted]))
        assertEquals(fixture.blindIndex.of("guest@example.com"), row[ClientTable.emailIndex])
    }

    @Test
    fun `should encrypt each synced integrated client under its own key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val legacy = Client.Integrated.stub(name = "Old", phone = "+10000000001", userId = userId)
        val integrated = suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, legacy) }

        whenn()
        suspendTransaction {
            fixture.sut.updateIntegratedClients(
                userId,
                name = "New",
                lastName = "Name",
                email = "new@example.com",
                phone = null,
                updatedAt = Instant.fromEpochSeconds(1_000),
            )
        }

        then()
        val row = fixture.storedRow(integrated.id)
        assertEquals("New", fixture.decrypted(integrated.id, row[ClientTable.nameEncrypted]))
        assertEquals("+10000000001", fixture.decrypted(integrated.id, row[ClientTable.phoneEncrypted]))
        assertEquals(fixture.blindIndex.of("new@example.com"), row[ClientTable.emailIndex])
    }

    @Test
    fun `should skip a synced integrated client whose key is shredded`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val created = suspendTransaction {
            fixture.sut.getOrCreateIntegratedClient(fixture.businessId, Client.Integrated.stub(userId = userId))
        }
        fixture.subjectKeys.shred(created.id)
        val before = fixture.storedRow(created.id)[ClientTable.nameEncrypted]

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateIntegratedClients(
                userId,
                name = "New",
                lastName = "Name",
                email = "new@example.com",
                phone = null,
                updatedAt = Instant.fromEpochSeconds(1_000),
            )
        }

        then()
        assertEquals(0, updated)
        assertTrue(before.contentEquals(fixture.storedRow(created.id)[ClientTable.nameEncrypted]))
    }

    @Test
    fun `should find a detached client to merge by its encrypted email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val integrated = suspendTransaction {
            fixture.sut.getOrCreateIntegratedClient(
                fixture.businessId,
                Client.Integrated.stub(email = "a@example.com", userId = userId),
            )
        }
        val detached = suspendTransaction {
            fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(email = "merge@example.com"))
        }

        whenn()
        val merges = suspendTransaction {
            fixture.sut.findClientMergesForUser(userId, "Merge@Example.com", Instant.fromEpochSeconds(1_000))
        }

        then()
        assertEquals(listOf(ClientMerge(fixture.businessId, detached.id, integrated.id)), merges)
    }
}
