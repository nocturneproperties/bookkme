package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.ClientEntity
import com.bookk.business.data.orm.entity.ClientEntity.Companion.storeContact
import com.bookk.business.data.orm.entity.ClientEntity.Companion.writeContact
import com.bookk.business.data.orm.entity.Contact
import com.bookk.business.data.orm.entity.ContactIndexes
import com.bookk.business.data.orm.entity.ContactReindex
import com.bookk.business.data.orm.entity.EncryptedContact
import com.bookk.business.data.orm.table.ClientNameTokenTable
import com.bookk.business.data.orm.table.ClientTable
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientMerge
import com.bookk.business.domain.api.client.entity.ClientUpdateModel
import com.bookk.business.domain.api.client.entity.ErasedClient
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.data.Keyset
import com.bookk.core.data.map.toDomain
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.Page
import com.bookk.core.domain.entity.PageCursor
import com.bookk.core.domain.entity.PageRequest
import com.bookk.core.domain.entity.toPage
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.orIfNotNull
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ClientDataSourceImpl(private val contactCipher: ContactCipher) : DataSource(), ClientDataSource {
    override suspend fun createDetachedClient(businessId: Uuid, client: Client.Detached): Client = dbQuery {
        val id = Uuid.random()
        val createdAt = insertClient(businessId, id, client.trimmedContact(), userId = null)
        client.copy(id = id, createdAt = createdAt)
    }

    override suspend fun createDetachedClients(
        businessId: Uuid,
        clients: List<Client.Detached>,
    ): List<Client> = dbQuery {
        val contacts = clients.map { it.trimmedContact() }
        val createdAt = storedNow()
        val rows = contacts.map { contact ->
            val id = Uuid.random()
            NewClientRow(id, encryptNew(id, contact), contactCipher.indexes(businessId, contact))
        }
        ClientTable.batchInsert(rows) { row ->
            this[ClientTable.id] = row.id
            this[ClientTable.businessId] = businessId
            this[ClientTable.userId] = null
            this[ClientTable.createdAt] = createdAt
            writeContact(row.encrypted, row.indexes)
        }
        ClientEntity.insertNameTokens(businessId, rows.associate { it.id to it.indexes.nameTokens })
        clients.zip(rows) { client, row -> client.copy(id = row.id, createdAt = createdAt) }
    }

    override suspend fun getClientsPage(businessId: Uuid, page: PageRequest, query: String?): Page<Client> = dbQuery {
        if (!query.isNullOrBlank()) return@dbQuery searchPage(businessId, page, contactCipher.search(businessId, query))
        val entities = pageOfClients(ClientTable.businessId eq businessId, page.after, page.fetchSize)
            .toPage(page) { it.pageCursor() }
        Page(entities.items.domains(), entities.next)
    }

    override suspend fun countClients(businessId: Uuid): Long = dbQuery {
        ClientTable.selectAll().where { ClientTable.businessId eq businessId }.count()
    }

    override suspend fun getClientsByEmails(businessId: Uuid, emails: Collection<String>): List<Client> = dbQuery {
        if (emails.isEmpty()) return@dbQuery emptyList()
        val indexes = emails.mapNotNull { contactCipher.emailIndex(it) }
        ClientEntity.find {
            (ClientTable.businessId eq businessId) and (ClientTable.emailIndex inList indexes)
        }
            .toList()
            .domains()
    }

    override suspend fun getClientById(businessId: Uuid, id: Uuid): Client? = dbQuery {
        ClientEntity.findById(id)
            ?.takeIf { it.businessId.value == businessId }
            ?.domain()
    }

    override suspend fun getClientForUpdate(businessId: Uuid, id: Uuid): Client? = dbQuery {
        ClientEntity.find { (ClientTable.businessId eq businessId) and (ClientTable.id eq id) }
            .forUpdate()
            .firstOrNull()
            ?.domain()
    }

    override suspend fun getClientByUserId(businessId: Uuid, userId: Uuid): Client? = dbQuery {
        findIntegratedClient(businessId, userId)
    }

    override suspend fun getOrCreateIntegratedClient(businessId: Uuid, client: Client.Integrated): Client = dbQuery {
        findIntegratedClient(businessId, client.userId)
            ?: runCatching { insertIntegratedClient(businessId, client) }
                .getOrElse { failure ->
                    if (failure.toDomain() !is Error.UniqueConstraintFailed) throw failure
                    findIntegratedClient(businessId, client.userId) ?: throw failure
                }
    }

    override suspend fun takeOverClient(
        businessId: Uuid,
        detachedClientId: Uuid,
        profile: Client.Integrated,
    ): Client? = dbQuery {
        val entity = ClientEntity.findById(detachedClientId)
            ?.takeIf { it.businessId.value == businessId && it.userId == null }
            ?: return@dbQuery null
        val read = entity.readForWrite()
        val current = read.contact
        val profileEmail = profile.email?.trim()
        val takenOver = current.copy(
            name = profile.name.trim(),
            lastName = profile.lastName.trim(),
            phone = profile.phone?.trim() ?: current.phone,
            email = profileEmail ?: current.email,
        )
        val reindex = ContactReindex(email = profileEmail != null, name = true, phone = profile.phone != null)
        if (!entity.write(read, takenOver, reindex)) return@dbQuery null
        entity.userId = profile.userId
        entity.lastActivityAt = Clock.System.now()
        entity.toDomain(takenOver)
    }

    override suspend fun findClientMergesForUser(
        userId: Uuid,
        email: String,
        updatedAt: Instant,
    ): List<ClientMerge> = dbQuery {
        val integrated = ClientTable.alias("integrated")
        val holdsEmail = ClientTable.emailIndex eq contactCipher.emailIndex(email)
        val integratedSourceUpdatedAt = integrated[ClientTable.sourceUpdatedAt]
        val integratedIsOutdated = integratedSourceUpdatedAt.isNull() or (integratedSourceUpdatedAt less updatedAt)
        ClientTable.join(integrated, JoinType.INNER, ClientTable.businessId, integrated[ClientTable.businessId])
            .select(ClientTable.businessId, ClientTable.id, integrated[ClientTable.id])
            .where {
                (integrated[ClientTable.userId] eq userId) and
                    integratedIsOutdated and
                    ClientTable.userId.isNull() and
                    holdsEmail
            }
            .map {
                ClientMerge(
                    businessId = it[ClientTable.businessId].value,
                    fromClientId = it[ClientTable.id].value,
                    toClientId = it[integrated[ClientTable.id]].value,
                )
            }
    }

    override suspend fun deleteClient(businessId: Uuid, id: Uuid) = dbQuery {
        ClientTable.deleteWhere {
            (ClientTable.businessId eq businessId) and (ClientTable.id eq id)
        } != 0
    }

    override suspend fun updateClient(businessId: Uuid, model: ClientUpdateModel): Client? = dbQuery {
        val entity = ClientEntity.find { (ClientTable.businessId eq businessId) and (ClientTable.id eq model.id) }
            .forUpdate()
            .singleOrNull()
            ?: return@dbQuery null
        val read = entity.readForWrite()
        val current = read.contact
        val edited = Contact(
            name = model.name ?: current.name,
            lastName = model.lastName ?: current.lastName,
            phone = model.phone ?: current.phone,
            email = model.email ?: current.email,
            description = model.description ?: current.description,
        )
        val reindex = ContactReindex(
            email = model.email != null,
            name = model.name != null || model.lastName != null,
            phone = model.phone != null,
        )
        if (!entity.write(read, edited, reindex)) return@dbQuery null
        entity.lastActivityAt = Clock.System.now()
        entity.toDomain(edited)
    }

    override suspend fun updateIntegratedClients(
        userId: Uuid,
        name: String,
        lastName: String,
        email: String,
        phone: String?,
        updatedAt: Instant,
    ): Int = dbQuery {
        val clients = ClientEntity.find {
            (ClientTable.userId eq userId) and
                (ClientTable.sourceUpdatedAt.isNull() or (ClientTable.sourceUpdatedAt less updatedAt))
        }.forUpdate().toList()
        val reads = contactCipher.readAll(clients.map { it.storedContact() }, SOURCE)
        clients.zip(reads).count { (entity, read) ->
            val synced = read.contact.copy(
                name = name.trim(),
                lastName = lastName.trim(),
                email = email.trim(),
                phone = phone?.trim() ?: read.contact.phone,
            )
            val reindex = ContactReindex(email = true, name = true, phone = phone != null)
            entity.write(read, synced, reindex).also { written ->
                if (written) entity.sourceUpdatedAt = updatedAt
            }
        }
    }

    override suspend fun getClientsOfUserForUpdate(userId: Uuid): List<ErasedClient> = dbQuery {
        ClientTable.select(ClientTable.businessId, ClientTable.id)
            .where { ClientTable.userId eq userId }
            .forUpdate()
            .map { ErasedClient(businessId = it[ClientTable.businessId].value, clientId = it[ClientTable.id].value) }
    }

    override suspend fun getClientIdsOfBusinessesForUpdate(businessIds: Collection<Uuid>): Set<Uuid> {
        if (businessIds.isEmpty()) return emptySet()
        return dbQuery {
            ClientTable.select(ClientTable.id)
                .where { ClientTable.businessId inList businessIds }
                .forUpdate()
                .map { it[ClientTable.id].value }
                .toSet()
        }
    }

    override suspend fun deleteClients(ids: Collection<Uuid>) {
        if (ids.isEmpty()) return
        dbQuery { ClientTable.deleteWhere { ClientTable.id inList ids } }
    }

    override suspend fun recordVisit(businessId: Uuid, clientId: Uuid, visitEnd: Instant) = dbQuery<Unit> {
        ClientTable.update(
            where = {
                (ClientTable.id eq clientId) and (ClientTable.businessId eq businessId) and
                    (ClientTable.lastActivityAt less visitEnd)
            },
        ) {
            it[lastActivityAt] = visitEnd
        }
    }

    override suspend fun getInactiveClientIdsForUpdate(businessId: Uuid, inactiveSince: Instant): List<Uuid> =
        dbQuery {
            ClientTable.select(ClientTable.id)
                .where {
                    (ClientTable.businessId eq businessId) and (ClientTable.lastActivityAt less inactiveSince) and
                        (ClientTable.deletionHold eq false)
                }
                .forUpdate()
                .map { it[ClientTable.id].value }
        }

    override suspend fun indexClientContacts(after: Uuid, limit: Int): Uuid? = dbQuery {
        val unindexed = findUnindexedClientsForUpdate(after, limit)
        val contacts = contactCipher.decryptAll(unindexed.map { it.storedContact() }, SOURCE)
        unindexed.zip(contacts).forEach { (client, contact) -> indexContact(client, contact) }
        unindexed.lastOrNull()?.id?.value
    }

    private fun findUnindexedClientsForUpdate(after: Uuid, limit: Int): List<ClientEntity> {
        return ClientEntity.find { (ClientTable.id greater after) and isUnindexed() }
            .orderBy(ClientTable.id to SortOrder.ASC)
            .limit(limit)
            .forUpdate()
            .toList()
    }

    private fun isUnindexed(): Op<Boolean> {
        val nameTokensOfClient = ClientNameTokenTable.select(ClientNameTokenTable.clientId)
            .where { ClientNameTokenTable.clientId eq ClientTable.id }
        val nameUnindexed = notExists(nameTokensOfClient)
        val phoneUnindexed = ClientTable.phoneEncrypted.isNotNull() and ClientTable.phoneIndex.isNull()
        return nameUnindexed or phoneUnindexed
    }

    private fun indexContact(client: ClientEntity, contact: Contact) {
        val nameTokens = contactCipher.nameTokens(client.businessId.value, contact.name, contact.lastName)
        val phoneIndex = contactCipher.phoneIndex(contact.phone)
        ClientEntity.findByIdAndUpdateSearchIndexes(client.id.value, nameTokens, phoneIndex)
    }

    private suspend fun searchPage(businessId: Uuid, page: PageRequest, search: ClientSearch): Page<Client> {
        val nameHasWord = search.wordTokens.map { tokens ->
            ClientTable.id inSubQuery ClientNameTokenTable
                .select(ClientNameTokenTable.clientId)
                .where {
                    (ClientNameTokenTable.businessId eq businessId) and (ClientNameTokenTable.token inList tokens)
                }
        }
        val nameMatches = if (nameHasWord.isEmpty()) Op.FALSE else nameHasWord.compoundAnd()
        val emailMatches = ClientTable.emailIndex eq search.emailIndex
        val phoneMatch = search.phoneIndex?.let { ClientTable.phoneIndex eq it }
        val matchesSearch = nameMatches or emailMatches orIfNotNull phoneMatch
        val matches = mutableListOf<MatchedClient>()
        var after = page.after
        repeat(MAX_SEARCH_BATCHES) {
            val batch = ClientEntity
                .find { (ClientTable.businessId eq businessId) and matchesSearch and pageKeyset.after(after) }
                .orderBy(*pageKeyset.orderBy())
                .limit(page.fetchSize)
                .toList()
            val contacts = contactCipher.decryptAll(batch.map { it.storedContact() }, SOURCE)
            batch.zip(contacts)
                .filter { (entity, contact) -> search.matches(entity.emailIndex, entity.phoneIndex, contact) }
                .mapTo(matches) { (entity, contact) -> MatchedClient(entity.toDomain(contact), entity.pageCursor()) }
            if (matches.size > page.limit) {
                val items = matches.take(page.limit)
                return Page(items.map { it.client }, next = items.last().cursor)
            }
            if (batch.size < page.fetchSize) return Page(matches.map { it.client }, next = null)
            after = batch.last().pageCursor()
        }
        return Page(matches.map { it.client }, next = after)
    }

    private fun pageOfClients(where: Op<Boolean>, after: PageCursor?, size: Int): List<ClientEntity> {
        return ClientEntity.find { where and pageKeyset.after(after) }
            .orderBy(*pageKeyset.orderBy())
            .limit(size)
            .toList()
    }

    private fun ClientEntity.pageCursor(): PageCursor {
        return PageCursor(after = createdAt, id = id.value)
    }

    private suspend fun findIntegratedClient(businessId: Uuid, userId: Uuid): Client? {
        return ClientEntity.find { (ClientTable.businessId eq businessId) and (ClientTable.userId eq userId) }
            .firstOrNull()
            ?.domain()
    }

    private suspend fun insertIntegratedClient(businessId: Uuid, client: Client.Integrated): Client {
        val id = Uuid.random()
        val createdAt = insertClient(businessId, id, client.trimmedContact(), userId = client.userId)
        return client.copy(id = id, createdAt = createdAt)
    }

    private suspend fun insertClient(businessId: Uuid, id: Uuid, contact: Contact, userId: Uuid?): Instant {
        val encrypted = encryptNew(id, contact)
        val indexes = contactCipher.indexes(businessId, contact)
        val createdAt = storedNow()
        ClientTable.insert {
            it[ClientTable.id] = id
            it[this.businessId] = businessId
            it[this.userId] = userId
            it[this.createdAt] = createdAt
            it.writeContact(encrypted, indexes)
        }
        ClientEntity.insertNameTokens(businessId, mapOf(id to indexes.nameTokens))
        return createdAt
    }

    private fun storedNow(): Instant {
        val now = Clock.System.now()
        val wholeMicroseconds = now.nanosecondsOfSecond / NANOSECONDS_PER_MICROSECOND * NANOSECONDS_PER_MICROSECOND
        return Instant.fromEpochSeconds(now.epochSeconds, wholeMicroseconds)
    }

    private suspend fun encryptNew(id: Uuid, contact: Contact): EncryptedContact {
        return checkNotNull(contactCipher.encrypt(id, contact)) { "The new client $id has a shredded key" }
    }

    private suspend fun ClientEntity.write(read: ReadContact, contact: Contact, reindex: ContactReindex): Boolean {
        val encrypted = contactCipher.encryptChanges(read, contact) ?: return false
        storeContact(encrypted, contactCipher.indexes(businessId.value, contact), reindex)
        return true
    }

    private suspend fun ClientEntity.readForWrite(): ReadContact {
        return contactCipher.read(storedContact(), SOURCE)
    }

    private suspend fun ClientEntity.domain(): Client {
        return toDomain(contactCipher.decrypt(storedContact(), SOURCE))
    }

    private suspend fun List<ClientEntity>.domains(): List<Client> {
        val contacts = contactCipher.decryptAll(map { it.storedContact() }, SOURCE)
        return zip(contacts) { entity, contact -> entity.toDomain(contact) }
    }

    private fun Client.trimmedContact(): Contact {
        return Contact(
            name = name.trim(),
            lastName = lastName.trim(),
            phone = phone?.trim(),
            email = email?.trim(),
            description = description?.trim(),
        )
    }

    private class MatchedClient(val client: Client, val cursor: PageCursor)

    private class NewClientRow(val id: Uuid, val encrypted: EncryptedContact, val indexes: ContactIndexes)

    private companion object {
        const val SOURCE = "client"
        const val NANOSECONDS_PER_MICROSECOND = 1_000
        const val MAX_SEARCH_BATCHES = 10
        val pageKeyset = Keyset(ClientTable.createdAt, ClientTable.id, SortOrder.DESC)
    }
}
