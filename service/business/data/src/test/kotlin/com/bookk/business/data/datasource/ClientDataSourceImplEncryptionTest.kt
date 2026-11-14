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
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.TimeZone
import library.encryption.Decrypted
import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import library.encryption.test.UnkeyedBlindIndex
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

        suspend fun setup() {
            businessId = suspendTransaction {
                businessSut.createBusiness(Uuid.random(), "Test Business", "USD", TimeZone.UTC)
            }.id
        }

        suspend fun storedRow(id: Uuid): ResultRow {
            return suspendTransaction { ClientTable.selectAll().where { ClientTable.id eq id }.single() }
        }

        suspend fun insertLegacy(client: Client, userId: Uuid? = null) {
            suspendTransaction {
                ClientTable.insert {
                    it[ClientTable.id] = client.id
                    it[businessId] = this@SutFixture.businessId
                    it[name] = client.name
                    it[lastName] = client.lastName
                    it[phone] = client.phone
                    it[email] = client.email
                    it[description] = client.description
                    it[this.userId] = userId
                }
            }
        }

        suspend fun decrypted(id: Uuid, ciphertext: ByteArray?): String? {
            val decryptor = subjectKeys.decryptor(listOf(id))
            return ciphertext?.let { (decryptor.decrypt(id, it) as Decrypted.Plain).value.decodeToString() }
        }
    }

    private fun emailEdit(id: Uuid, email: String): ClientUpdateModel {
        return ClientUpdateModel(id = id, name = null, lastName = null, phone = null, email = email, description = null)
    }

    @Test
    fun `should store a client encrypted under the client's key and no plaintext`() = runUnitTest {
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
        assertNull(row[ClientTable.name])
        assertNull(row[ClientTable.lastName])
        assertNull(row[ClientTable.phone])
        assertNull(row[ClientTable.email])
        assertNull(row[ClientTable.description])
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
            assertNull(row[ClientTable.name])
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
    fun `should find a plaintext client by email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val legacy = Client.Detached.stub(email = "legacy@example.com")
        fixture.insertLegacy(legacy)

        whenn()
        val found = suspendTransaction {
            fixture.sut.getClientsByEmails(fixture.businessId, listOf("legacy@example.com"))
        }

        then()
        assertEquals(listOf(legacy), found)
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
    fun `should reject a client whose email a plaintext client holds`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        fixture.insertLegacy(Client.Detached.stub(email = "held@example.com"))

        whenn()
        val result = runCatching {
            suspendTransaction {
                fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(email = "held@example.com"))
            }
        }

        then()
        assertTrue(result.exceptionOrNull()?.toDomain() is Error.UniqueConstraintFailed) { result.toString() }
    }

    @Test
    fun `should reject changing a client's email to one a plaintext client holds`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        fixture.insertLegacy(Client.Detached.stub(email = "held@example.com"))
        val created = suspendTransaction {
            fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(email = "own@example.com"))
        }

        whenn()
        val result = runCatching {
            suspendTransaction {
                fixture.sut.updateClient(fixture.businessId, emailEdit(created.id, "held@example.com"))
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
    fun `should read a plaintext client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val legacy = Client.Detached.stub(name = "Legacy", description = "old note")
        fixture.insertLegacy(legacy)

        whenn()
        val read = suspendTransaction { fixture.sut.getClientById(fixture.businessId, legacy.id) }

        then()
        assertEquals(legacy, read)
    }

    @Test
    fun `should encrypt a plaintext client when it is edited`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val legacy = Client.Detached.stub(name = "Legacy", email = "legacy@example.com", description = "note")
        fixture.insertLegacy(legacy)

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateClient(
                fixture.businessId,
                ClientUpdateModel(
                    id = legacy.id,
                    name = "Renamed",
                    lastName = null,
                    phone = null,
                    email = null,
                    description = null,
                ),
            )
        }

        then()
        val row = fixture.storedRow(legacy.id)
        assertEquals(legacy.copy(name = "Renamed"), updated)
        assertNull(row[ClientTable.name])
        assertNull(row[ClientTable.email])
        assertNull(row[ClientTable.description])
        assertEquals("Renamed", fixture.decrypted(legacy.id, row[ClientTable.nameEncrypted]))
        assertEquals("note", fixture.decrypted(legacy.id, row[ClientTable.descriptionEncrypted]))
        assertEquals(fixture.blindIndex.of("legacy@example.com"), row[ClientTable.emailIndex])
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
        val read = suspendTransaction { fixture.sut.getClients(fixture.businessId) }

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
        assertNull(row[ClientTable.name])
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
        fixture.insertLegacy(legacy)
        val profile = Client.Integrated.stub(name = "Guest", email = "guest@example.com", userId = Uuid.random())

        whenn()
        val takenOver = suspendTransaction { fixture.sut.takeOverClient(fixture.businessId, legacy.id, profile) }

        then()
        val row = fixture.storedRow(legacy.id)
        assertEquals("Guest", takenOver?.name)
        assertEquals("note", takenOver?.description)
        assertNull(row[ClientTable.name])
        assertEquals("Guest", fixture.decrypted(legacy.id, row[ClientTable.nameEncrypted]))
        assertEquals(fixture.blindIndex.of("guest@example.com"), row[ClientTable.emailIndex])
    }

    @Test
    fun `should encrypt each synced integrated client under its own key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val legacy = Client.Integrated.stub(name = "Old", phone = "+10000000001", userId = userId)
        fixture.insertLegacy(legacy, userId = userId)

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
        val row = fixture.storedRow(legacy.id)
        assertNull(row[ClientTable.name])
        assertNull(row[ClientTable.phone])
        assertEquals("New", fixture.decrypted(legacy.id, row[ClientTable.nameEncrypted]))
        assertEquals("+10000000001", fixture.decrypted(legacy.id, row[ClientTable.phoneEncrypted]))
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

    @Test
    fun `should find a plaintext detached client to merge by its email`() = runUnitTest {
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
        val legacy = Client.Detached.stub(email = "merge@example.com")
        fixture.insertLegacy(legacy)

        whenn()
        val merges = suspendTransaction {
            fixture.sut.findClientMergesForUser(userId, "merge@example.com", Instant.fromEpochSeconds(1_000))
        }

        then()
        assertEquals(listOf(ClientMerge(fixture.businessId, legacy.id, integrated.id)), merges)
    }

    @Test
    fun `should encrypt plaintext clients in batches`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val first = Client.Detached.stub(name = "First", email = "first@example.com")
        val second = Client.Detached.stub(name = "Second", email = "second@example.com")
        fixture.insertLegacy(first)
        fixture.insertLegacy(second)
        suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(email = null)) }

        whenn()
        val firstBatch = suspendTransaction { fixture.sut.encryptPlaintextClients(limit = 1) }
        val secondBatch = suspendTransaction { fixture.sut.encryptPlaintextClients(limit = 1) }
        val thirdBatch = suspendTransaction { fixture.sut.encryptPlaintextClients(limit = 1) }

        then()
        assertEquals(listOf(1, 1, 0), listOf(firstBatch, secondBatch, thirdBatch))
        listOf(first, second).forEach { client ->
            val row = fixture.storedRow(client.id)
            assertNull(row[ClientTable.name])
            assertNull(row[ClientTable.email])
            assertEquals(client.name, fixture.decrypted(client.id, row[ClientTable.nameEncrypted]))
            assertEquals(fixture.blindIndex.of(client.email!!), row[ClientTable.emailIndex])
        }
    }
}
