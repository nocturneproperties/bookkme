package com.bookk.business.domain.datasource

import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientMerge
import com.bookk.business.domain.api.client.entity.ClientUpdateModel
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface ClientDataSource {
    suspend fun createDetachedClient(businessId: Uuid, client: Client.Detached): Client
    suspend fun createDetachedClients(businessId: Uuid, clients: List<Client.Detached>): List<Client>
    suspend fun getClients(businessId: Uuid): List<Client>
    suspend fun getClientsByContacts(businessId: Uuid, phones: Collection<String>, emails: Collection<String>): List<Client>
    suspend fun getClientById(businessId: Uuid, id: Uuid): Client?
    suspend fun getClientByUserId(businessId: Uuid, userId: Uuid): Client?
    suspend fun getOrCreateIntegratedClient(businessId: Uuid, client: Client.Integrated): Client
    suspend fun takeOverClient(businessId: Uuid, detachedClientId: Uuid, profile: Client.Integrated): Client?
    suspend fun findClientMergesForUser(userId: Uuid, phone: String?, email: String, updatedAt: Instant): List<ClientMerge>
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