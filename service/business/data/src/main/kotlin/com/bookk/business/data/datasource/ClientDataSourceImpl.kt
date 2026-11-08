package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.ClientEntity
import com.bookk.business.data.orm.table.ClientTable
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientMerge
import com.bookk.business.domain.api.client.entity.ClientUpdateModel
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.data.map.toDomain
import com.bookk.core.domain.entity.Error
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ClientDataSourceImpl : DataSource(), ClientDataSource {
    override suspend fun createDetachedClient(businessId: Uuid, client: Client.Detached): Client = dbQuery {
        val id = ClientTable.insertAndGetId {
            it[this.businessId] = businessId
            it[name] = client.name.trim()
            it[lastName] = client.lastName.trim()
            it[phone] = client.phone?.trim()
            it[email] = client.email?.trim()
            it[userId] = null
            it[description] = client.description?.trim()
        }
        client.copy(
            id = id.value,
        )
    }

    override suspend fun createDetachedClients(businessId: Uuid, clients: List<Client.Detached>): List<Client> = dbQuery {
        ClientTable.batchInsert(clients) { client ->
            this[ClientTable.businessId] = businessId
            this[ClientTable.name] = client.name.trim()
            this[ClientTable.lastName] = client.lastName.trim()
            this[ClientTable.phone] = client.phone?.trim()
            this[ClientTable.email] = client.email?.trim()
            this[ClientTable.userId] = null
            this[ClientTable.description] = client.description?.trim()
        }.zip(clients) { row, client -> client.copy(id = row[ClientTable.id].value) }
    }

    override suspend fun getClients(businessId: Uuid): List<Client> = dbQuery {
        ClientEntity.find {
            ClientTable.businessId eq businessId
        }.map(ClientEntity::toDomain)
    }

    override suspend fun getClientsByEmails(businessId: Uuid, emails: Collection<String>): List<Client> = dbQuery {
        if (emails.isEmpty()) return@dbQuery emptyList()
        ClientEntity.find { (ClientTable.businessId eq businessId) and (ClientTable.email inList emails) }
            .map(ClientEntity::toDomain)
    }

    override suspend fun getClientById(businessId: Uuid, id: Uuid): Client? = dbQuery {
        ClientEntity.findById(id)
            ?.takeIf { it.businessId.value == businessId }
            ?.toDomain()
    }

    override suspend fun getClientForUpdate(businessId: Uuid, id: Uuid): Client? = dbQuery {
        ClientEntity.find { (ClientTable.businessId eq businessId) and (ClientTable.id eq id) }
            .forUpdate()
            .firstOrNull()
            ?.toDomain()
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

    override suspend fun takeOverClient(businessId: Uuid, detachedClientId: Uuid, profile: Client.Integrated): Client? = dbQuery {
        ClientEntity.takeOver(businessId, detachedClientId, profile)?.toDomain()
    }

    override suspend fun findClientMergesForUser(userId: Uuid, email: String, updatedAt: Instant): List<ClientMerge> = dbQuery {
        val integrated = ClientTable.alias("integrated")
        ClientTable.join(integrated, JoinType.INNER, ClientTable.businessId, integrated[ClientTable.businessId])
            .select(ClientTable.businessId, ClientTable.id, integrated[ClientTable.id])
            .where {
                (integrated[ClientTable.userId] eq userId) and
                    (integrated[ClientTable.sourceUpdatedAt].isNull() or (integrated[ClientTable.sourceUpdatedAt] less updatedAt)) and
                    ClientTable.userId.isNull() and
                    (ClientTable.email eq email.trim())
            }
            .map {
                ClientMerge(
                    businessId = it[ClientTable.businessId].value,
                    fromClientId = it[ClientTable.id].value,
                    toClientId = it[integrated[ClientTable.id]].value,
                )
            }
    }

    private fun findIntegratedClient(businessId: Uuid, userId: Uuid): Client? = ClientEntity.find {
        (ClientTable.businessId eq businessId) and (ClientTable.userId eq userId)
    }
        .map(ClientEntity::toDomain)
        .firstOrNull()

    private fun insertIntegratedClient(businessId: Uuid, client: Client.Integrated): Client {
        val id = ClientTable.insertAndGetId {
            it[this.businessId] = businessId
            it[name] = client.name.trim()
            it[lastName] = client.lastName.trim()
            it[phone] = client.phone?.trim()
            it[email] = client.email?.trim()
            it[userId] = client.userId
            it[description] = client.description?.trim()
        }
        return client.copy(id = id.value)
    }

    override suspend fun deleteClient(businessId: Uuid, id: Uuid) = dbQuery {
        ClientTable.deleteWhere {
            (ClientTable.businessId eq businessId) and (ClientTable.id eq id)
        } != 0
    }

    override suspend fun updateClient(businessId: Uuid, model: ClientUpdateModel): Client? = dbQuery {
        ClientEntity.findByIdAndUpdate(businessId, model)?.toDomain()
    }

    override suspend fun updateIntegratedClients(
        userId: Uuid,
        name: String,
        lastName: String,
        email: String,
        phone: String?,
        updatedAt: Instant,
    ): Int = dbQuery {
        ClientTable.update(
            where = {
                (ClientTable.userId eq userId) and
                    (ClientTable.sourceUpdatedAt.isNull() or (ClientTable.sourceUpdatedAt less updatedAt))
            },
        ) {
            it[this.name] = name.trim()
            it[this.lastName] = lastName.trim()
            it[this.email] = email.trim()
            phone?.let { value -> it[this.phone] = value.trim() }
            it[sourceUpdatedAt] = updatedAt
        }
    }

    override suspend fun deleteClientsByUserId(userId: Uuid) = dbQuery<Unit> {
        ClientTable.deleteWhere { ClientTable.userId eq userId }
    }

    override suspend fun getBusinessIdsByUserId(userId: Uuid): List<Uuid> = dbQuery {
        ClientTable.select(ClientTable.businessId)
            .where { ClientTable.userId eq userId }
            .map { it[ClientTable.businessId].value }
    }

    override suspend fun recordVisit(businessId: Uuid, clientId: Uuid, visitEnd: Instant) = dbQuery<Unit> {
        ClientTable.update(
            where = { (ClientTable.id eq clientId) and (ClientTable.businessId eq businessId) and (ClientTable.lastActivityAt less visitEnd) },
        ) {
            it[lastActivityAt] = visitEnd
        }
    }

    override suspend fun deleteInactiveClients(businessId: Uuid, inactiveSince: Instant): List<Uuid> = dbQuery {
        val inactiveIds = ClientTable.select(ClientTable.id)
            .where { (ClientTable.businessId eq businessId) and (ClientTable.lastActivityAt less inactiveSince) }
            .forUpdate()
            .map { it[ClientTable.id].value }
        if (inactiveIds.isNotEmpty()) {
            ClientTable.deleteWhere { (ClientTable.businessId eq businessId) and (ClientTable.id inList inactiveIds) }
        }
        inactiveIds
    }
}
