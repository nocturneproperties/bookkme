package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.ClientEntity
import com.bookk.business.data.orm.entity.ClientEntity.Companion.writeContact
import com.bookk.business.data.orm.entity.Contact
import com.bookk.business.data.orm.entity.EncryptedContact
import com.bookk.business.data.orm.table.ClientTable
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientMerge
import com.bookk.business.domain.api.client.entity.ClientUpdateModel
import com.bookk.business.domain.api.client.entity.ErasedClient
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.data.map.toDomain
import com.bookk.core.domain.entity.Error
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ClientDataSourceImpl(private val contactCipher: ContactCipher) : DataSource(), ClientDataSource {
    override suspend fun createDetachedClient(businessId: Uuid, client: Client.Detached): Client = dbQuery {
        val id = Uuid.random()
        insertClient(businessId, id, client.trimmedContact(), userId = null)
        client.copy(id = id)
    }

    override suspend fun createDetachedClients(
        businessId: Uuid,
        clients: List<Client.Detached>,
    ): List<Client> = dbQuery {
        val contacts = clients.map { it.trimmedContact() }
        requireNoPlaintextHolder(businessId, contacts.mapNotNull { it.email })
        val rows = contacts.map { contact ->
            val id = Uuid.random()
            NewClientRow(id, contact, encryptNew(id, contact))
        }
        ClientTable.batchInsert(rows) { row ->
            this[ClientTable.id] = row.id
            this[ClientTable.businessId] = businessId
            this[ClientTable.userId] = null
            writeContact(row.encrypted, contactCipher.emailIndex(row.contact.email))
        }
        clients.zip(rows) { client, row -> client.copy(id = row.id) }
    }

    override suspend fun getClients(businessId: Uuid): List<Client> = dbQuery {
        ClientEntity.find { ClientTable.businessId eq businessId }.toList().domains()
    }

    override suspend fun getClientsByEmails(businessId: Uuid, emails: Collection<String>): List<Client> = dbQuery {
        if (emails.isEmpty()) return@dbQuery emptyList()
        val indexes = emails.mapNotNull { contactCipher.emailIndex(it) }
        val matchesEmail = (ClientTable.emailIndex inList indexes) or (ClientTable.email inList emails)
        ClientEntity.find { (ClientTable.businessId eq businessId) and matchesEmail }.toList().domains()
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
        val current = entity.readContact()
        val profileEmail = profile.email?.trim()
        val takenOver = current.copy(
            name = profile.name.trim(),
            lastName = profile.lastName.trim(),
            phone = profile.phone?.trim() ?: current.phone,
            email = profileEmail ?: current.email,
        )
        if (profileEmail != null) requireNoPlaintextHolder(businessId, listOf(profileEmail), except = entity.id.value)
        if (!entity.write(takenOver, reindexEmail = profileEmail != null)) return@dbQuery null
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
        val holdsEmail = (ClientTable.emailIndex eq contactCipher.emailIndex(email)) or
            (ClientTable.email eq email.trim())
        ClientTable.join(integrated, JoinType.INNER, ClientTable.businessId, integrated[ClientTable.businessId])
            .select(ClientTable.businessId, ClientTable.id, integrated[ClientTable.id])
            .where {
                (integrated[ClientTable.userId] eq userId) and
                    (
                        integrated[ClientTable.sourceUpdatedAt].isNull() or
                            (integrated[ClientTable.sourceUpdatedAt] less updatedAt)
                        ) and
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
        val current = entity.readContact()
        val edited = Contact(
            name = model.name ?: current.name,
            lastName = model.lastName ?: current.lastName,
            phone = model.phone ?: current.phone,
            email = model.email ?: current.email,
            description = model.description ?: current.description,
        )
        model.email?.let { requireNoPlaintextHolder(businessId, listOf(it), except = model.id) }
        if (!entity.write(edited, reindexEmail = model.email != null)) return@dbQuery null
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
        val reader = contactCipher.reader(clients.map { it.id.value }, SOURCE)
        clients.count { entity ->
            val current = entity.contact(reader)
            val synced = current.copy(
                name = name.trim(),
                lastName = lastName.trim(),
                email = email.trim(),
                phone = phone?.trim() ?: current.phone,
            )
            entity.write(synced, reindexEmail = true).also { written ->
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

    override suspend fun getBusinessIdsByUserId(userId: Uuid): List<Uuid> = dbQuery {
        ClientTable.select(ClientTable.businessId)
            .where { ClientTable.userId eq userId }
            .map { it[ClientTable.businessId].value }
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

    override suspend fun encryptPlaintextClients(limit: Int): Int = dbQuery {
        val plaintext = ClientEntity.find { ClientTable.nameEncrypted.isNull() }.limit(limit).forUpdate().toList()
        plaintext.forEach { entity ->
            val written = entity.write(entity.plaintextContact(), reindexEmail = true)
            check(written) { "The stored client ${entity.id.value} has a shredded key" }
        }
        plaintext.size
    }

    private suspend fun findIntegratedClient(businessId: Uuid, userId: Uuid): Client? {
        return ClientEntity.find { (ClientTable.businessId eq businessId) and (ClientTable.userId eq userId) }
            .firstOrNull()
            ?.domain()
    }

    private suspend fun insertIntegratedClient(businessId: Uuid, client: Client.Integrated): Client {
        val id = Uuid.random()
        insertClient(businessId, id, client.trimmedContact(), userId = client.userId)
        return client.copy(id = id)
    }

    private suspend fun insertClient(businessId: Uuid, id: Uuid, contact: Contact, userId: Uuid?) {
        requireNoPlaintextHolder(businessId, listOfNotNull(contact.email))
        val encrypted = encryptNew(id, contact)
        ClientTable.insert {
            it[ClientTable.id] = id
            it[this.businessId] = businessId
            it[this.userId] = userId
            it.writeContact(encrypted, contactCipher.emailIndex(contact.email))
        }
    }

    private suspend fun encryptNew(id: Uuid, contact: Contact): EncryptedContact {
        return checkNotNull(contactCipher.encrypt(id, contact)) { "The new client $id has a shredded key" }
    }

    private suspend fun ClientEntity.write(contact: Contact, reindexEmail: Boolean): Boolean {
        val encrypted = contactCipher.encrypt(id.value, contact) ?: return false
        store(encrypted, contactCipher.emailIndex(contact.email), reindexEmail)
        return true
    }

    private suspend fun ClientEntity.readContact(): Contact {
        return contact(contactCipher.reader(listOf(id.value), SOURCE))
    }

    private suspend fun ClientEntity.domain(): Client {
        return toDomain(readContact())
    }

    private suspend fun List<ClientEntity>.domains(): List<Client> {
        val reader = contactCipher.reader(map { it.id.value }, SOURCE)
        return map { it.toDomain(it.contact(reader)) }
    }

    private fun requireNoPlaintextHolder(businessId: Uuid, emails: Collection<String>, except: Uuid? = null) {
        if (emails.isEmpty()) return
        val someoneElse = except?.let { ClientTable.id neq it } ?: Op.TRUE
        val held = ClientTable.select(ClientTable.id)
            .where { (ClientTable.businessId eq businessId) and (ClientTable.email inList emails) and someoneElse }
            .limit(1)
            .any()
        if (held) throw Error.UniqueConstraintFailed("A plaintext client holds this email", IllegalStateException())
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

    private class NewClientRow(val id: Uuid, val contact: Contact, val encrypted: EncryptedContact)

    private companion object {
        const val SOURCE = "client"
    }
}
