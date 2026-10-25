package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.ClientEntity
import com.bookk.business.data.orm.table.ClientTable
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientUpdateModel
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.data.map.toDomain
import com.bookk.core.domain.entity.Error
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
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
            id = id.value
        )
    }

    override suspend fun createIntegratedClient(businessId: Uuid, client: Client.Integrated): Client = dbQuery {
        insertIntegratedClient(businessId, client)
    }

    override suspend fun getClients(businessId: Uuid): List<Client> = dbQuery {
        ClientEntity.find {
            ClientTable.businessId eq businessId
        }.map(ClientEntity::toDomain)
    }

    override suspend fun getClient(businessId: Uuid, phone: String): Client? = dbQuery {
        ClientEntity.find {
            (ClientTable.businessId eq businessId) and (ClientTable.phone eq phone)
        }
            .map(ClientEntity::toDomain)
            .firstOrNull()
    }

    override suspend fun getClientById(businessId: Uuid, id: Uuid): Client? = dbQuery {
        ClientEntity.findById(id)
            ?.takeIf { it.businessId.value == businessId }
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

    private fun findIntegratedClient(businessId: Uuid, userId: Uuid): Client? =
        ClientEntity.find {
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
        updatedAt: Instant
    ): Int = dbQuery {
        ClientTable.update(
            where = {
                (ClientTable.userId eq userId) and
                    (ClientTable.sourceUpdatedAt.isNull() or (ClientTable.sourceUpdatedAt less updatedAt))
            }
        ) {
            it[this.name] = name.trim()
            it[this.lastName] = lastName.trim()
            it[this.email] = email.trim()
            phone?.let { value -> it[this.phone] = value.trim() }
            it[sourceUpdatedAt] = updatedAt
        }
    }

    override suspend fun anonymizeClientsByUserId(userId: Uuid): Int = dbQuery {
        ClientTable.update(where = { ClientTable.userId eq userId }) {
            it[name] = "Deleted User"
            it[lastName] = ""
            it[phone] = null
            it[email] = null
            it[this.userId] = null
        }
    }

    override suspend fun getBusinessIdsByUserId(userId: Uuid): List<Uuid> = dbQuery {
        ClientTable.select(ClientTable.businessId)
            .where { ClientTable.userId eq userId }
            .map { it[ClientTable.businessId].value }
    }
}