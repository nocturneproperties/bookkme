package com.bookk.business.domain.datasource

import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientUpdateModel
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface ClientDataSource {
    suspend fun createDetachedClient(businessId: Uuid, client: Client.Detached): Client
    suspend fun createIntegratedClient(businessId: Uuid, client: Client.Integrated): Client
    suspend fun getClients(businessId: Uuid): List<Client>
    suspend fun getClient(businessId: Uuid, phone: String): Client?
    suspend fun getClientById(businessId: Uuid, id: Uuid): Client?
    suspend fun getClientByUserId(businessId: Uuid, userId: Uuid): Client?
    suspend fun getOrCreateIntegratedClient(businessId: Uuid, client: Client.Integrated): Client
    suspend fun deleteClient(businessId: Uuid, id: Uuid): Boolean
    suspend fun updateClient(businessId: Uuid, model: ClientUpdateModel): Client?
    suspend fun updateIntegratedClients(
        userId: Uuid,
        name: String,
        lastName: String,
        email: String,
        phone: String?,
        updatedAt: Instant
    ): Int
    suspend fun anonymizeClientsByUserId(userId: Uuid): Int
    suspend fun getBusinessIdsByUserId(userId: Uuid): List<Uuid>
}