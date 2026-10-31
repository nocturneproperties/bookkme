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
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ClientDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(BusinessTable, BusinessDashboardTable, BusinessPermissionGrantsTable, BusinessWorkingHoursTable, BusinessDayOffTable, ClientTable)
        val sut = ClientDataSourceImpl()
        val businessSut = BusinessDataSourceImpl()
        lateinit var businessId: Uuid

        suspend fun setup() {
            businessId = suspendTransaction {
                businessSut.createBusiness(Uuid.random(), "Test Business", "USD", TimeZone.UTC)
            }.id
        }
    }

    @Test
    fun `should create detached client and retrieve by business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val client = Client.Detached.stub(name = "Alice")

        whenn()
        suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, client) }
        val clients = suspendTransaction { fixture.sut.getClients(fixture.businessId) }

        then()
        assertEquals(1, clients.size)
        assertEquals("Alice", clients.first().name)
    }

    @Test
    fun `should create detached client with a description`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val client = Client.Detached.stub(description = "Prefers evening appointments")

        whenn()
        val created = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, client) }
        val found = suspendTransaction { fixture.sut.getClientById(fixture.businessId, created.id) }

        then()
        assertEquals("Prefers evening appointments", found!!.description)
    }

    @Test
    fun `should create detached client with no email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val client = Client.Detached.stub(email = null)

        whenn()
        val created = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, client) }
        val found = suspendTransaction { fixture.sut.getClientById(fixture.businessId, created.id) }

        then()
        assertNotNull(found)
        assertNull(found!!.email)
    }

    @Test
    fun `should create detached client with no phone`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val client = Client.Detached.stub(phone = null)

        whenn()
        val created = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, client) }
        val found = suspendTransaction { fixture.sut.getClientById(fixture.businessId, created.id) }

        then()
        assertNotNull(found)
        assertNull(found!!.phone)
    }

    @Test
    fun `should return empty list when no clients exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val clients = suspendTransaction { fixture.sut.getClients(fixture.businessId) }

        then()
        assertTrue(clients.isEmpty())
    }

    @Test
    fun `should retrieve client by id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val client = Client.Detached.stub(name = "Erin")
        val created = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, client) }

        whenn()
        val found = suspendTransaction { fixture.sut.getClientById(fixture.businessId, created.id) }

        then()
        assertNotNull(found)
        assertEquals("Erin", found!!.name)
    }

    @Test
    fun `should return null when client not found by id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val found = suspendTransaction { fixture.sut.getClientById(fixture.businessId, Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should return null when client id belongs to a different business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        val client = Client.Detached.stub()
        val created = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, client) }

        whenn()
        val found = suspendTransaction { fixture.sut.getClientById(otherBusinessId, created.id) }

        then()
        assertNull(found)
    }

    @Test
    fun `should delete client and return true`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val client = Client.Detached.stub()
        val created = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, client) }

        whenn()
        val deleted = suspendTransaction { fixture.sut.deleteClient(fixture.businessId, created.id) }
        val remaining = suspendTransaction { fixture.sut.getClients(fixture.businessId) }

        then()
        assertTrue(deleted)
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun `should return false when deleting non-existent client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val deleted = suspendTransaction { fixture.sut.deleteClient(fixture.businessId, Uuid.random()) }

        then()
        assertTrue(!deleted)
    }

    @Test
    fun `should update client personal info and description`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val client = Client.Detached.stub()
        val created = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, client) }
        val model = ClientUpdateModel(
            id = created.id, name = "Grant2", lastName = "Hill2",
            phone = "+7778889998", email = "grant2@test.com", description = "Allergic to nut-based products"
        )

        whenn()
        val updated = suspendTransaction { fixture.sut.updateClient(fixture.businessId, model) }

        then()
        assertNotNull(updated)
        assertEquals("Grant2", updated!!.name)
        assertEquals("Hill2", updated.lastName)
        assertEquals("+7778889998", updated.phone)
        assertEquals("grant2@test.com", updated.email)
        assertEquals("Allergic to nut-based products", updated.description)
    }

    @Test
    fun `should leave fields absent from the update model unchanged`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val client = Client.Detached.stub(description = "Old notes")
        val created = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, client) }
        val model = ClientUpdateModel(
            id = created.id, name = null, lastName = null, phone = null, email = null, description = "New notes"
        )

        whenn()
        val updated = suspendTransaction { fixture.sut.updateClient(fixture.businessId, model) }

        then()
        assertNotNull(updated)
        assertEquals(client.name, updated!!.name)
        assertEquals(client.phone, updated.phone)
        assertEquals("New notes", updated.description)
    }

    @Test
    fun `should return null when updating a client that does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val model = ClientUpdateModel(id = Uuid.random(), name = null, lastName = null, phone = null, email = null, description = "notes")

        whenn()
        val updated = suspendTransaction { fixture.sut.updateClient(fixture.businessId, model) }

        then()
        assertNull(updated)
    }

    @Test
    fun `should not update client when it belongs to a different business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        val client = Client.Detached.stub()
        val created = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, client) }
        val model = ClientUpdateModel(id = created.id, name = null, lastName = null, phone = null, email = null, description = "notes")

        whenn()
        val updated = suspendTransaction { fixture.sut.updateClient(otherBusinessId, model) }
        val found = suspendTransaction { fixture.sut.getClientById(fixture.businessId, created.id) }

        then()
        assertNull(updated)
        assertNull(found!!.description)
    }

    @Test
    fun `should update integrated client fields by user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val client = Client.Integrated.stub(userId = userId)
        suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, client) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateIntegratedClients(userId, "New", "Surname", "new@test.com", null, Instant.fromEpochMilliseconds(1000))
        }
        val found = suspendTransaction { fixture.sut.getClientByUserId(fixture.businessId, userId) }

        then()
        assertEquals(1, updated)
        assertNotNull(found)
        assertTrue(found is Client.Integrated)
        assertEquals("New", found!!.name)
        assertEquals("Surname", found.lastName)
        assertEquals("new@test.com", found.email)
        assertEquals(userId, (found as Client.Integrated).userId)
    }

    @Test
    fun `should update the client phone when the profile supplies one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val client = Client.Integrated.stub(userId = userId)
        suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, client) }

        whenn()
        suspendTransaction {
            fixture.sut.updateIntegratedClients(
                userId, "New", "Surname", "new@test.com", "+2222222222", Instant.fromEpochMilliseconds(1000)
            )
        }

        then()
        val found = suspendTransaction { fixture.sut.getClientByUserId(fixture.businessId, userId) }
        assertEquals("+2222222222", found!!.phone)
    }

    @Test
    fun `should keep the client phone when the profile has none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val client = Client.Integrated.stub(userId = userId)
        suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, client) }

        whenn()
        suspendTransaction {
            fixture.sut.updateIntegratedClients(
                userId, "New", "Surname", "new@test.com", null, Instant.fromEpochMilliseconds(1000)
            )
        }

        then()
        val found = suspendTransaction { fixture.sut.getClientByUserId(fixture.businessId, userId) }
        assertEquals(client.phone, found!!.phone)
        assertEquals("New", found.name)
    }

    @Test
    fun `should ignore a profile update older than the one already applied`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val client = Client.Integrated.stub(userId = userId)
        suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, client) }
        val newer = Instant.fromEpochMilliseconds(2000)
        val older = Instant.fromEpochMilliseconds(1000)
        suspendTransaction { fixture.sut.updateIntegratedClients(userId, "Newer", "Surname", "newer@test.com", null, newer) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateIntegratedClients(userId, "Older", "Surname", "older@test.com", null, older)
        }
        val found = suspendTransaction { fixture.sut.getClientByUserId(fixture.businessId, userId) }

        then()
        assertEquals(0, updated)
        assertEquals("Newer", found!!.name)
        assertEquals("newer@test.com", found.email)
    }

    @Test
    fun `should apply a profile update newer than the one already applied`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val client = Client.Integrated.stub(userId = userId)
        suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, client) }
        suspendTransaction {
            fixture.sut.updateIntegratedClients(userId, "First", "Surname", "first@test.com", null, Instant.fromEpochMilliseconds(1000))
        }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateIntegratedClients(userId, "Second", "Surname", "second@test.com", null, Instant.fromEpochMilliseconds(2000))
        }
        val found = suspendTransaction { fixture.sut.getClientByUserId(fixture.businessId, userId) }

        then()
        assertEquals(1, updated)
        assertEquals("Second", found!!.name)
    }

    @Test
    fun `should not update detached clients when updating by user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val detached = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(name = "Keep")) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateIntegratedClients(Uuid.random(), "New", "Surname", "new@test.com", null, Instant.fromEpochMilliseconds(1000))
        }
        val found = suspendTransaction { fixture.sut.getClientById(fixture.businessId, detached.id) }

        then()
        assertEquals(0, updated)
        assertNotNull(found)
        assertEquals("Keep", found!!.name)
    }

    @Test
    fun `should anonymize integrated client PII and detach the user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val client = Client.Integrated.stub(userId = userId)
        val created = suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, client) }

        whenn()
        val affected = suspendTransaction { fixture.sut.anonymizeClientsByUserId(userId) }
        val found = suspendTransaction { fixture.sut.getClients(fixture.businessId) }.single { it.id == created.id }

        then()
        assertEquals(1, affected)
        assertEquals("Deleted User", found.name)
        assertEquals("", found.lastName)
        assertNull(found.phone)
        assertNull(found.email)
        assertTrue(found is Client.Detached)
    }

    @Test
    fun `should return zero when anonymizing clients for a user with none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val affected = suspendTransaction { fixture.sut.anonymizeClientsByUserId(Uuid.random()) }

        then()
        assertEquals(0, affected)
    }

    @Test
    fun `should return client by user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val client = Client.Integrated.stub(name = "Grace", userId = userId)
        suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, client) }

        whenn()
        val found = suspendTransaction { fixture.sut.getClientByUserId(fixture.businessId, userId) }

        then()
        assertNotNull(found)
        assertEquals("Grace", found!!.name)
    }

    @Test
    fun `should return null when no client exists for user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val found = suspendTransaction { fixture.sut.getClientByUserId(fixture.businessId, Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should return null when the client for user id belongs to a different business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        val client = Client.Integrated.stub(userId = userId)
        suspendTransaction { fixture.sut.getOrCreateIntegratedClient(otherBusinessId, client) }

        whenn()
        val found = suspendTransaction { fixture.sut.getClientByUserId(fixture.businessId, userId) }

        then()
        assertNull(found)
    }

    @Test
    fun `should create a new integrated client when none exists for user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val client = Client.Integrated.stub(name = "Henry", userId = userId)

        whenn()
        val result = suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, client) }

        then()
        assertTrue(result is Client.Integrated)
        assertEquals(userId, (result as Client.Integrated).userId)
        assertEquals("Henry", result.name)
        val clients = suspendTransaction { fixture.sut.getClients(fixture.businessId) }
        assertEquals(1, clients.size)
    }

    @Test
    fun `should return business ids for a client user id across multiple businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        val userId = Uuid.random()
        suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, Client.Integrated.stub(userId = userId)) }
        suspendTransaction { fixture.sut.getOrCreateIntegratedClient(otherBusinessId, Client.Integrated.stub(userId = userId)) }

        whenn()
        val businessIds = suspendTransaction { fixture.sut.getBusinessIdsByUserId(userId) }

        then()
        assertEquals(setOf(fixture.businessId, otherBusinessId), businessIds.toSet())
    }

    @Test
    fun `should return empty list of business ids for a user with no client relations`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val businessIds = suspendTransaction { fixture.sut.getBusinessIdsByUserId(Uuid.random()) }

        then()
        assertTrue(businessIds.isEmpty())
    }

    @Test
    fun `should not return business ids for detached clients`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub()) }

        whenn()
        val businessIds = suspendTransaction { fixture.sut.getBusinessIdsByUserId(Uuid.random()) }

        then()
        assertTrue(businessIds.isEmpty())
    }

    @Test
    fun `should return the existing integrated client when one already exists for user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val existing = Client.Integrated.stub(name = "Iris", userId = userId)
        val created = suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, existing) }
        val attempt = Client.Integrated.stub(name = "Different", userId = userId)

        whenn()
        val result = suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, attempt) }

        then()
        assertEquals(created.id, result.id)
        assertEquals("Iris", result.name)
        val clients = suspendTransaction { fixture.sut.getClients(fixture.businessId) }
        assertEquals(1, clients.size)
    }

    @Test
    fun `should reject a second client with the same phone in one business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+1555", email = "first@client.com")) }

        whenn()
        val result = runCatching {
            suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+1555", email = "second@client.com")) }
        }

        then()
        assertTrue(result.exceptionOrNull() is Error.UniqueConstraintFailed)
    }

    @Test
    fun `should reject a second client with the same email in one business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+1111", email = "same@client.com")) }

        whenn()
        val result = runCatching {
            suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+2222", email = "same@client.com")) }
        }

        then()
        assertTrue(result.exceptionOrNull() is Error.UniqueConstraintFailed)
    }

    @Test
    fun `should reject an integrated client whose phone a detached client already holds`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+1555", email = null)) }

        whenn()
        val result = runCatching {
            suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, Client.Integrated.stub(phone = "+1555", email = "user@client.com")) }
        }

        then()
        assertTrue(result.exceptionOrNull() is Error.UniqueConstraintFailed)
    }

    @Test
    fun `should allow the same phone and email in different businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub()) }

        whenn()
        val result = runCatching {
            suspendTransaction { fixture.sut.createDetachedClient(otherBusinessId, Client.Detached.stub()) }
        }

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should allow several clients without a phone or without an email in one business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val result = runCatching {
            suspendTransaction {
                fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = null, email = "a@client.com"))
                fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = null, email = "b@client.com"))
                fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+1111", email = null))
                fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+2222", email = null))
            }
        }

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should find clients of the business holding any of the given phones or emails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        val byPhone = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+1111", email = null)) }
        val byEmail = suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, Client.Integrated.stub(phone = null, email = "match@client.com")) }
        suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+9999", email = "other@client.com")) }
        suspendTransaction { fixture.sut.createDetachedClient(otherBusinessId, Client.Detached.stub(phone = "+1111", email = "match@client.com")) }

        whenn()
        val found = suspendTransaction {
            fixture.sut.getClientsByContacts(fixture.businessId, phones = listOf("+1111", "+3333"), emails = listOf("match@client.com"))
        }

        then()
        assertEquals(setOf(byPhone.id, byEmail.id), found.map { it.id }.toSet())
    }

    @Test
    fun `should find no clients when no phones or emails are given`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub()) }

        whenn()
        val found = suspendTransaction { fixture.sut.getClientsByContacts(fixture.businessId, phones = emptyList(), emails = emptyList()) }

        then()
        assertTrue(found.isEmpty())
    }

    @Test
    fun `should create detached clients in one batch`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clients = listOf(
            Client.Detached.stub(name = "Ann", phone = "+1111", email = null, description = "Regular"),
            Client.Detached.stub(name = "Ben", phone = null, email = "ben@client.com")
        )

        whenn()
        val created = suspendTransaction { fixture.sut.createDetachedClients(fixture.businessId, clients) }

        then()
        assertEquals(listOf("Ann", "Ben"), created.map { it.name })
        val stored = suspendTransaction { fixture.sut.getClients(fixture.businessId) }
        assertEquals(created.map { it.id }.toSet(), stored.map { it.id }.toSet())
        assertEquals("Regular", stored.single { it.name == "Ann" }.description)
        assertTrue(stored.all { it is Client.Detached })
    }

    @Test
    fun `should reject a batch that repeats a phone`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clients = listOf(
            Client.Detached.stub(phone = "+1111", email = null),
            Client.Detached.stub(phone = "+1111", email = null)
        )

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.createDetachedClients(fixture.businessId, clients) } }

        then()
        assertTrue(result.exceptionOrNull() is Error.UniqueConstraintFailed)
    }

    @Test
    fun `should take over a detached client keeping its id and description`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val detached = suspendTransaction {
            fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(name = "Old", phone = "+1111", email = null, description = "Allergic to latex"))
        }
        val userId = Uuid.random()
        val profile = Client.Integrated.stub(name = "New", lastName = "Name", phone = "+1111", email = "user@client.com", userId = userId)

        whenn()
        val taken = suspendTransaction { fixture.sut.takeOverClient(fixture.businessId, detached.id, profile) }

        then()
        val stored = suspendTransaction { fixture.sut.getClientByUserId(fixture.businessId, userId) }
        assertEquals(detached.id, taken?.id)
        assertEquals(detached.id, stored?.id)
        assertEquals("New", stored?.name)
        assertEquals("Name", stored?.lastName)
        assertEquals("user@client.com", stored?.email)
        assertEquals("Allergic to latex", stored?.description)
    }

    @Test
    fun `should keep the detached phone when the taking over profile has none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val detached = suspendTransaction {
            fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+1111", email = "user@client.com"))
        }
        val profile = Client.Integrated.stub(phone = null, email = "user@client.com")

        whenn()
        val taken = suspendTransaction { fixture.sut.takeOverClient(fixture.businessId, detached.id, profile) }

        then()
        assertEquals("+1111", taken?.phone)
    }

    @Test
    fun `should not take over a client of another business or an integrated one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        val foreign = suspendTransaction { fixture.sut.createDetachedClient(otherBusinessId, Client.Detached.stub()) }
        val integrated = suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, Client.Integrated.stub()) }
        val profile = Client.Integrated.stub(phone = null, email = "taker@client.com")

        whenn()
        val takenForeign = suspendTransaction { fixture.sut.takeOverClient(fixture.businessId, foreign.id, profile) }
        val takenIntegrated = suspendTransaction { fixture.sut.takeOverClient(fixture.businessId, integrated.id, profile) }

        then()
        assertNull(takenForeign)
        assertNull(takenIntegrated)
    }

    @Test
    fun `should find detached clients that clash with the user's new contacts where the user is integrated`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        val userId = Uuid.random()
        val integrated = suspendTransaction {
            fixture.sut.getOrCreateIntegratedClient(fixture.businessId, Client.Integrated.stub(phone = "+1000", email = "old@client.com", userId = userId))
        }
        val byPhone = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+2000", email = null)) }
        val byEmail = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = null, email = "new@client.com")) }
        suspendTransaction { fixture.sut.createDetachedClient(otherBusinessId, Client.Detached.stub(phone = "+2000", email = null)) }

        whenn()
        val merges = suspendTransaction {
            fixture.sut.findClientMergesForUser(userId, phone = "+2000", email = "new@client.com", updatedAt = Instant.fromEpochMilliseconds(1000))
        }

        then()
        assertEquals(
            setOf(
                ClientMerge(fixture.businessId, fromClientId = byPhone.id, toClientId = integrated.id),
                ClientMerge(fixture.businessId, fromClientId = byEmail.id, toClientId = integrated.id)
            ),
            merges.toSet()
        )
    }

    @Test
    fun `should match only the email when the user has no phone`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, Client.Integrated.stub(phone = null, email = "old@client.com", userId = userId)) }
        suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+2000", email = null)) }

        whenn()
        val merges = suspendTransaction {
            fixture.sut.findClientMergesForUser(userId, phone = null, email = "new@client.com", updatedAt = Instant.fromEpochMilliseconds(1000))
        }

        then()
        assertTrue(merges.isEmpty())
    }

    @Test
    fun `should not merge for a profile update older than the one already applied`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, Client.Integrated.stub(phone = "+1000", email = "user@client.com", userId = userId)) }
        suspendTransaction {
            fixture.sut.updateIntegratedClients(userId, "Name", "Last", "user@client.com", "+1000", Instant.fromEpochMilliseconds(2000))
        }
        suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+2000", email = null)) }

        whenn()
        val merges = suspendTransaction {
            fixture.sut.findClientMergesForUser(userId, phone = "+2000", email = "user@client.com", updatedAt = Instant.fromEpochMilliseconds(1000))
        }

        then()
        assertTrue(merges.isEmpty())
    }

    @Test
    fun `should reject updating a client to the phone of another client in the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+1111", email = null)) }
        val other = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+2222", email = null)) }

        whenn()
        val result = runCatching {
            suspendTransaction {
                fixture.sut.updateClient(
                    fixture.businessId,
                    ClientUpdateModel(id = other.id, name = null, lastName = null, phone = "+1111", email = null, description = null)
                )
            }
        }

        then()
        assertTrue(result.exceptionOrNull()?.toDomain() is Error.UniqueConstraintFailed)
    }

    @Test
    fun `should allow updating a client with its own phone and email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val client = suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, Client.Detached.stub(phone = "+1111", email = "own@client.com")) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateClient(
                fixture.businessId,
                ClientUpdateModel(id = client.id, name = null, lastName = null, phone = "+1111", email = "own@client.com", description = "notes")
            )
        }

        then()
        assertEquals("notes", updated?.description)
    }
}
