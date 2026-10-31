package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientImportResult
import com.bookk.business.domain.api.client.entity.RejectedClient
import com.bookk.business.domain.api.client.entity.toRemote
import com.bookk.business.domain.api.client.operation.ImportClients
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.onConstraintFailure
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class ImportClientsImpl(
    private val transactionManager: TransactionManager,
    private val clientDataSource: ClientDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource
) : ImportClients {
    override suspend fun invoke(
        requestUserId: Uuid,
        businessId: Uuid,
        clients: List<Client.Detached>
    ): Result<ClientImportResult> = transactionManager.transaction {
        businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS)
            .assert(PermissionAction.UPDATE)
        if (clients.size > ImportClients.MAX_CLIENTS) throw ImportClients.Error.TooManyClients()

        val rejected = mutableListOf<RejectedClient>()
        val validated = clients.withIndex().mapNotNull { (index, client) ->
            runCatching { IndexedValue(index, client.validatedFields()) }
                .onFailure { failure ->
                    if (failure !is BusinessError) throw failure
                    rejected += RejectedClient(index, failure.code)
                }
                .getOrNull()
        }

        val existing = clientDataSource.getClientsByContacts(
            businessId,
            phones = validated.mapNotNull { it.value.phone }.distinct(),
            emails = validated.mapNotNull { it.value.email }.distinct()
        )
        val takenPhones = existing.mapNotNull { it.phone }.toMutableSet()
        val takenEmails = existing.mapNotNull { it.email?.lowercase() }.toMutableSet()

        val accepted = validated.mapNotNull { (index, fields) ->
            val phoneTaken = fields.phone != null && fields.phone in takenPhones
            val emailTaken = fields.email != null && fields.email.lowercase() in takenEmails
            if (phoneTaken || emailTaken) {
                rejected += RejectedClient(index, BusinessErrorCodes.BUSINESS_CLIENT_EXISTS)
                return@mapNotNull null
            }
            fields.phone?.let(takenPhones::add)
            fields.email?.let { takenEmails += it.lowercase() }
            fields.toDetachedClient()
        }

        val created = if (accepted.isEmpty()) emptyList() else clientDataSource.createDetachedClients(businessId, accepted)
        ClientImportResult(
            created = created.map(Client::toRemote),
            rejected = rejected.sortedBy(RejectedClient::index)
        )
    }.onConstraintFailure { throw ImportClients.Error.ClientExist() }

    private fun ClientFields.toDetachedClient() = Client.Detached(
        id = Uuid.random(),
        name = name,
        lastName = lastName,
        phone = phone,
        email = email,
        description = description
    )
}
