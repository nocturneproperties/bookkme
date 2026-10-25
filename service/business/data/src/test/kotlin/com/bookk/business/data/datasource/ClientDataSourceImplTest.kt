package com.bookk.business.data.datasource

import com.bookk.business.data.orm.table.BusinessDashboardTable
import com.bookk.business.data.orm.table.BusinessDayOffTable
import com.bookk.business.data.orm.table.BusinessPermissionGrantsTable
import com.bookk.business.data.orm.table.BusinessTable
import com.bookk.business.data.orm.table.BusinessWorkingHoursTable
import com.bookk.business.data.orm.table.ClientTable
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientUpdateModel
import com.bookk.core.data.test.createTestDatabase
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
    fun `should create integrated client with user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val client = Client.Integrated.stub(userId = userId)

        whenn()
        val created = suspendTransaction { fixture.sut.createIntegratedClient(fixture.businessId, client) }

        then()
        assertTrue(created is Client.Integrated)
        assertEquals(userId, (created as Client.Integrated).userId)
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
    fun `should retrieve client by phone`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val client = Client.Detached.stub(name = "Carol")
        suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, client) }

        whenn()
        val found = suspendTransaction { fixture.sut.getClient(fixture.businessId, client.phone!!) }

        then()
        assertNotNull(found)
        assertEquals("Carol", found!!.name)
    }

    @Test
    fun `should return null when client not found by phone`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val found = suspendTransaction { fixture.sut.getClient(fixture.businessId, "+0000000000") }

        then()
        assertNull(found)
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
        suspendTransaction { fixture.sut.createIntegratedClient(fixture.businessId, client) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateIntegratedClients(userId, "New", "Surname", "new@test.com", null, Instant.fromEpochMilliseconds(1000))
        }
        val found = suspendTransaction { fixture.sut.getClient(fixture.businessId, client.phone!!) }

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
        suspendTransaction { fixture.sut.createIntegratedClient(fixture.businessId, client) }

        whenn()
        suspendTransaction {
            fixture.sut.updateIntegratedClients(
                userId, "New", "Surname", "new@test.com", "+2222222222", Instant.fromEpochMilliseconds(1000)
            )
        }

        then()
        val found = suspendTransaction { fixture.sut.getClient(fixture.businessId, "+2222222222") }
        assertEquals("+2222222222", found!!.phone)
    }

    @Test
    fun `should keep the client phone when the profile has none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val client = Client.Integrated.stub(userId = userId)
        suspendTransaction { fixture.sut.createIntegratedClient(fixture.businessId, client) }

        whenn()
        suspendTransaction {
            fixture.sut.updateIntegratedClients(
                userId, "New", "Surname", "new@test.com", null, Instant.fromEpochMilliseconds(1000)
            )
        }

        then()
        val found = suspendTransaction { fixture.sut.getClient(fixture.businessId, client.phone!!) }
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
        suspendTransaction { fixture.sut.createIntegratedClient(fixture.businessId, client) }
        val newer = Instant.fromEpochMilliseconds(2000)
        val older = Instant.fromEpochMilliseconds(1000)
        suspendTransaction { fixture.sut.updateIntegratedClients(userId, "Newer", "Surname", "newer@test.com", null, newer) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateIntegratedClients(userId, "Older", "Surname", "older@test.com", null, older)
        }
        val found = suspendTransaction { fixture.sut.getClient(fixture.businessId, client.phone!!) }

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
        suspendTransaction { fixture.sut.createIntegratedClient(fixture.businessId, client) }
        suspendTransaction {
            fixture.sut.updateIntegratedClients(userId, "First", "Surname", "first@test.com", null, Instant.fromEpochMilliseconds(1000))
        }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateIntegratedClients(userId, "Second", "Surname", "second@test.com", null, Instant.fromEpochMilliseconds(2000))
        }
        val found = suspendTransaction { fixture.sut.getClient(fixture.businessId, client.phone!!) }

        then()
        assertEquals(1, updated)
        assertEquals("Second", found!!.name)
    }

    @Test
    fun `should not update detached clients when updating by user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val detached = Client.Detached.stub(name = "Keep")
        suspendTransaction { fixture.sut.createDetachedClient(fixture.businessId, detached) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateIntegratedClients(Uuid.random(), "New", "Surname", "new@test.com", null, Instant.fromEpochMilliseconds(1000))
        }
        val found = suspendTransaction { fixture.sut.getClient(fixture.businessId, detached.phone!!) }

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
        val created = suspendTransaction { fixture.sut.createIntegratedClient(fixture.businessId, client) }

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
        suspendTransaction { fixture.sut.createIntegratedClient(fixture.businessId, client) }

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
        suspendTransaction { fixture.sut.createIntegratedClient(otherBusinessId, client) }

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
        suspendTransaction { fixture.sut.createIntegratedClient(fixture.businessId, Client.Integrated.stub(userId = userId)) }
        suspendTransaction { fixture.sut.createIntegratedClient(otherBusinessId, Client.Integrated.stub(userId = userId)) }

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
        val created = suspendTransaction { fixture.sut.createIntegratedClient(fixture.businessId, existing) }
        val attempt = Client.Integrated.stub(name = "Different", userId = userId)

        whenn()
        val result = suspendTransaction { fixture.sut.getOrCreateIntegratedClient(fixture.businessId, attempt) }

        then()
        assertEquals(created.id, result.id)
        assertEquals("Iris", result.name)
        val clients = suspendTransaction { fixture.sut.getClients(fixture.businessId) }
        assertEquals(1, clients.size)
    }
}
