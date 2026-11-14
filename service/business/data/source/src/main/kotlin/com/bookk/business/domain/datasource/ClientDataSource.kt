package com.bookk.business.domain.datasource

import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientMerge
import com.bookk.business.domain.api.client.entity.ClientUpdateModel
import com.bookk.business.domain.api.client.entity.ErasedClient
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface ClientDataSource {
    suspend fun createDetachedClient(businessId: Uuid, client: Client.Detached): Client
    suspend fun createDetachedClients(businessId: Uuid, clients: List<Client.Detached>): List<Client>
    suspend fun getClients(businessId: Uuid): List<Client>
    suspend fun getClientsByEmails(businessId: Uuid, emails: Collection<String>): List<Client>
    suspend fun getClientById(businessId: Uuid, id: Uuid): Client?
    suspend fun getClientForUpdate(businessId: Uuid, id: Uuid): Client?
    suspend fun getClientByUserId(businessId: Uuid, userId: Uuid): Client?
    suspend fun getOrCreateIntegratedClient(businessId: Uuid, client: Client.Integrated): Client
    suspend fun takeOverClient(businessId: Uuid, detachedClientId: Uuid, profile: Client.Integrated): Client?
    suspend fun findClientMergesForUser(userId: Uuid, email: String, updatedAt: Instant): List<ClientMerge>
    suspend fun deleteClient(businessId: Uuid, id: Uuid): Boolean
    suspend fun updateClient(businessId: Uuid, model: ClientUpdateModel): Client?
    suspend fun updateIntegratedClients(
        userId: Uuid,
        name: String,
        lastName: String,
        email: String,
        phone: String?,
        updatedAt: Instant,
    ): Int
    suspend fun getClientsOfUserForUpdate(userId: Uuid): List<ErasedClient>
    suspend fun getClientIdsOfBusinessesForUpdate(businessIds: Collection<Uuid>): Set<Uuid>
    suspend fun deleteClients(ids: Collection<Uuid>)
    suspend fun getBusinessIdsByUserId(userId: Uuid): List<Uuid>
    suspend fun recordVisit(businessId: Uuid, clientId: Uuid, visitEnd: Instant)
    suspend fun getInactiveClientIdsForUpdate(businessId: Uuid, inactiveSince: Instant): List<Uuid>
    suspend fun encryptPlaintextClients(limit: Int): Int
}
