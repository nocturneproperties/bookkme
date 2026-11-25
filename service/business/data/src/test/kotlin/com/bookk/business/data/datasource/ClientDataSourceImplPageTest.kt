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
import com.bookk.core.domain.entity.PageCursor
import com.bookk.core.domain.entity.pageRequest
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.TimeZone
import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import library.encryption.test.UnkeyedBlindIndex
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ClientDataSourceImplPageTest {

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
        val sut = ClientDataSourceImpl(ContactCipher(subjectKeys, blindIndex, RecordingMissingKeys()))
        val businessSut = BusinessDataSourceImpl()
        lateinit var businessId: Uuid

        suspend fun setup() {
            businessId = createBusiness()
        }

        suspend fun createBusiness(): Uuid {
            return suspendTransaction {
                businessSut.createBusiness(Uuid.random(), "Test Business", "USD", TimeZone.UTC)
            }.id
        }

        suspend fun create(client: Client.Detached, businessId: Uuid = this.businessId): Client {
            return suspendTransaction { sut.createDetachedClient(businessId, client) }
        }

        suspend fun createdAt(clientId: Uuid, at: Instant) {
            suspendTransaction { ClientTable.update(where = { ClientTable.id eq clientId }) { it[createdAt] = at } }
        }

        suspend fun search(query: String?, after: PageCursor? = null, limit: Int = 10): List<Uuid> {
            return suspendTransaction {
                sut.getClientsPage(businessId, pageRequest(limit, after), query)
            }.items.map { it.id }
        }

        suspend fun storedRow(id: Uuid): ResultRow {
            return suspendTransaction { ClientTable.selectAll().where { ClientTable.id eq id }.single() }
        }

        suspend fun clearIndexes(ids: List<Uuid>) {
            suspendTransaction {
                ClientTable.update(where = { ClientTable.id inList ids }) {
                    it[nameIndex] = null
                    it[phoneIndex] = null
                }
            }
        }
    }

    private fun alice(): Client.Detached {
        return Client.Detached.stub(name = "Alice", lastName = "Smith", phone = "+10000000001", email = "a@example.com")
    }

    private fun bob(): Client.Detached {
        return Client.Detached.stub(name = "Bob", lastName = "Jones", phone = "+10000000002", email = "b@example.com")
    }

    private fun edit(
        id: Uuid,
        name: String? = null,
        lastName: String? = null,
        phone: String? = null,
        description: String? = null,
    ): ClientUpdateModel {
        return ClientUpdateModel(
            id = id,
            name = name,
            lastName = lastName,
            phone = phone,
            email = null,
            description = description,
        )
    }

    @Test
    fun `should page the business's clients newest first`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clients = (0 until 3).map { day ->
            fixture.create(Client.Detached.stub(email = null)).also {
                fixture.createdAt(it.id, Instant.fromEpochSeconds(1_800_000_000L + day * 86_400L))
            }
        }
        fixture.create(Client.Detached.stub(email = null), businessId = fixture.createBusiness())
        val first = suspendTransaction { fixture.sut.getClientsPage(fixture.businessId, pageRequest(2), null) }

        whenn()
        val second = suspendTransaction {
            fixture.sut.getClientsPage(fixture.businessId, pageRequest(2, first.next), null)
        }

        then()
        assertEquals(listOf(clients[2].id, clients[1].id), first.items.map { it.id })
        assertEquals(listOf(clients[0].id), second.items.map { it.id })
        assertNull(second.next)
    }

    @Test
    fun `should page clients created at the same time without gaps or repeats`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val createdAt = Instant.fromEpochSeconds(1_800_000_000)
        val clients = (0 until 5).map { fixture.create(Client.Detached.stub(email = null)) }
        clients.forEach { fixture.createdAt(it.id, createdAt) }

        whenn()
        val pages = mutableListOf<List<Uuid>>()
        var after: PageCursor? = null
        do {
            val page =
                suspendTransaction { fixture.sut.getClientsPage(fixture.businessId, pageRequest(2, after), null) }
            pages += page.items.map { it.id }
            after = page.next
        } while (after != null)

        then()
        assertEquals(listOf(2, 2, 1), pages.map { it.size })
        assertEquals(clients.map { it.id }.toSet(), pages.flatten().toSet())
    }

    @Test
    fun `should give imported clients a creation time`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val before = Clock.System.now()

        whenn()
        val imported =
            suspendTransaction { fixture.sut.createDetachedClients(fixture.businessId, listOf(alice(), bob())) }

        then()
        assertEquals(imported.map { it.id }.toSet(), fixture.search(null).toSet())
        imported.forEach { assertTrue(fixture.storedRow(it.id)[ClientTable.createdAt] >= before - 1.seconds) }
    }

    @Test
    fun `should find a client by the whole name ignoring case and spacing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val match = fixture.create(alice())
        fixture.create(bob())

        whenn()
        val found = fixture.search("  aLiCe    SMITH ")

        then()
        assertEquals(listOf(match.id), found)
    }

    @Test
    fun `should find a client by email ignoring case`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val match = fixture.create(alice())
        fixture.create(bob())

        whenn()
        val found = fixture.search(" A@Example.com ")

        then()
        assertEquals(listOf(match.id), found)
    }

    @Test
    fun `should find a client by phone typed with spaces and dashes`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val match = fixture.create(alice())
        fixture.create(bob())

        whenn()
        val found = fixture.search("+1 000-000-0001")

        then()
        assertEquals(listOf(match.id), found)
    }

    @Test
    fun `should not find a client by part of a name, email or phone`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        fixture.create(alice())

        whenn()
        val found = listOf("alice", "a@example", "0001").flatMap { fixture.search(it) }

        then()
        assertEquals(emptyList<Uuid>(), found)
    }

    @Test
    fun `should not find another business's client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        fixture.create(alice(), businessId = fixture.createBusiness())

        whenn()
        val found = fixture.search("alice smith")

        then()
        assertEquals(emptyList<Uuid>(), found)
    }

    @Test
    fun `should apply the search on the pages after the cursor`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val matches = (0 until 3).map { day ->
            fixture.create(alice().copy(email = null)).also {
                fixture.createdAt(it.id, Instant.fromEpochSeconds(1_800_000_000L + day * 86_400L))
            }
        }
        fixture.create(bob())
        val first = suspendTransaction { fixture.sut.getClientsPage(fixture.businessId, pageRequest(2), "alice smith") }

        whenn()
        val second = fixture.search("alice smith", after = first.next, limit = 2)

        then()
        assertEquals(listOf(matches[2].id, matches[1].id), first.items.map { it.id })
        assertEquals(listOf(matches[0].id), second)
    }

    @Test
    fun `should index the name and phone of a new client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val created = fixture.create(alice())

        then()
        val row = fixture.storedRow(created.id)
        assertEquals(fixture.blindIndex.of("alice smith"), row[ClientTable.nameIndex])
        assertEquals(fixture.blindIndex.of("10000000001"), row[ClientTable.phoneIndex])
    }

    @Test
    fun `should leave the phone index empty for a client without a phone`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val created = fixture.create(alice().copy(phone = null))

        then()
        assertNull(fixture.storedRow(created.id)[ClientTable.phoneIndex])
    }

    @Test
    fun `should reindex the name and phone of an edited client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(alice())

        whenn()
        suspendTransaction {
            fixture.sut.updateClient(fixture.businessId, edit(created.id, lastName = "Jones", phone = "+19999999999"))
        }

        then()
        assertEquals(listOf(created.id), fixture.search("alice jones"))
        assertEquals(listOf(created.id), fixture.search("+19999999999"))
        assertEquals(emptyList<Uuid>(), fixture.search("alice smith"))
    }

    @Test
    fun `should keep unedited name and phone indexes of a client whose key is missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = fixture.create(alice())
        fixture.subjectKeys.loseKey(created.id)

        whenn()
        suspendTransaction { fixture.sut.updateClient(fixture.businessId, edit(created.id, description = "new")) }

        then()
        val row = fixture.storedRow(created.id)
        assertEquals(fixture.blindIndex.of("alice smith"), row[ClientTable.nameIndex])
        assertEquals(fixture.blindIndex.of("10000000001"), row[ClientTable.phoneIndex])
    }

    @Test
    fun `should index the profile name of a taken over client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val detached = fixture.create(alice())
        val profile = Client.Integrated.stub(name = "Carol", lastName = "White", phone = "+15555555555")

        whenn()
        suspendTransaction { fixture.sut.takeOverClient(fixture.businessId, detached.id, profile) }

        then()
        assertEquals(listOf(detached.id), fixture.search("carol white"))
        assertEquals(listOf(detached.id), fixture.search("+15555555555"))
    }

    @Test
    fun `should index the synced profile name and phone of an integrated client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val integrated = suspendTransaction {
            fixture.sut.getOrCreateIntegratedClient(fixture.businessId, Client.Integrated.stub())
        } as Client.Integrated

        whenn()
        suspendTransaction {
            fixture.sut.updateIntegratedClients(
                userId = integrated.userId,
                name = "Dave",
                lastName = "Brown",
                email = "dave@example.com",
                phone = "+16666666666",
                updatedAt = Clock.System.now(),
            )
        }

        then()
        assertEquals(listOf(integrated.id), fixture.search("dave brown"))
        assertEquals(listOf(integrated.id), fixture.search("+16666666666"))
        assertEquals(listOf(integrated.id), fixture.search("dave@example.com"))
    }

    @Test
    fun `should index clients stored without indexes and return the last one's id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clients = listOf(fixture.create(alice()), fixture.create(bob()))
        fixture.clearIndexes(clients.map { it.id })
        val last = clients.maxBy { it.id }

        whenn()
        val next = suspendTransaction { fixture.sut.indexClientContacts(after = Uuid.NIL, limit = 10) }

        then()
        assertEquals(last.id, next)
        assertEquals(listOf(clients[0].id), fixture.search("alice smith"))
        assertEquals(listOf(clients[1].id), fixture.search("+10000000002"))
    }

    @Test
    fun `should report no further clients once every client is indexed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        fixture.create(alice())

        whenn()
        val next = suspendTransaction { fixture.sut.indexClientContacts(after = Uuid.NIL, limit = 10) }

        then()
        assertNull(next)
    }

    @Test
    fun `should move past a client whose key is missing without indexing it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val unreadable = fixture.create(alice())
        fixture.clearIndexes(listOf(unreadable.id))
        fixture.subjectKeys.loseKey(unreadable.id)

        whenn()
        val next = suspendTransaction { fixture.sut.indexClientContacts(after = Uuid.NIL, limit = 10) }

        then()
        assertEquals(unreadable.id, next)
        assertNull(fixture.storedRow(unreadable.id)[ClientTable.nameIndex])
        assertNull(fixture.storedRow(unreadable.id)[ClientTable.phoneIndex])
    }

    @Test
    fun `should index at most the given number of clients after the given id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clients = listOf(fixture.create(alice()), fixture.create(bob())).sortedBy { it.id }
        fixture.clearIndexes(clients.map { it.id })

        whenn()
        val next = suspendTransaction { fixture.sut.indexClientContacts(after = Uuid.NIL, limit = 1) }

        then()
        assertEquals(clients[0].id, next)
        assertNotNull(fixture.storedRow(clients[0].id)[ClientTable.nameIndex])
        assertNull(fixture.storedRow(clients[1].id)[ClientTable.nameIndex])
    }
}
