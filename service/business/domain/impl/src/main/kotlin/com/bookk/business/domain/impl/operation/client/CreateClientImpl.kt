package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientRemote
import com.bookk.business.domain.api.client.entity.toRemote
import com.bookk.business.domain.api.client.operation.CreateClient
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.permissions.PermissionAction
import library.permissions.assert
import library.validation.EmailValidator
import library.validation.NameValidator
import library.validation.PhoneValidator
import kotlin.uuid.Uuid

internal class CreateClientImpl(
    private val transactionManager: TransactionManager,
    private val clientDataSource: ClientDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource
) : CreateClient {
    override suspend fun invoke(requestUserId: Uuid, businessId: Uuid, client: Client): Result<ClientRemote> =
        transactionManager.transaction {
            businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS)
                .assert(PermissionAction.UPDATE)
            if (!NameValidator.isValid(client.name, minLength = 0, maxLength = MAX_NAME_LENGTH)) {
                throw CreateClient.Error.ClientValidationError()
            }
            if (!NameValidator.isValid(client.lastName, minLength = 0, maxLength = MAX_NAME_LENGTH)) {
                throw CreateClient.Error.ClientValidationError()
            }
            val phone = client.phone?.takeIf { it.isNotBlank() }?.also { phone ->
                if (!PhoneValidator.isValid(phone)) throw CreateClient.Error.ClientValidationError()
                if (clientDataSource.getClient(businessId, phone) != null) throw CreateClient.Error.ClientExist()
            }
            val email = client.email?.takeIf { it.isNotBlank() }?.also { email ->
                if (!EmailValidator.isValid(email)) throw CreateClient.Error.ClientValidationError()
            }
            if (phone == null && email == null) throw CreateClient.Error.MissingContactInfo()

            val trimmedClient = when (client) {
                is Client.Detached -> client.copy(
                    name = client.name.trim(),
                    lastName = client.lastName.trim(),
                    phone = phone?.trim(),
                    email = email?.trim(),
                    description = client.description?.trim()?.take(Client.MAX_DESCRIPTION_LENGTH)
                )
                is Client.Integrated -> client.copy(
                    name = client.name.trim(),
                    lastName = client.lastName.trim(),
                    phone = phone?.trim(),
                    email = email?.trim(),
                    description = client.description?.trim()?.take(Client.MAX_DESCRIPTION_LENGTH)
                )
            }

            when (trimmedClient) {
                is Client.Detached -> clientDataSource.createDetachedClient(businessId, trimmedClient)
                is Client.Integrated -> clientDataSource.createIntegratedClient(businessId, trimmedClient)
            }.toRemote()
        }

    companion object {
        const val MAX_NAME_LENGTH = 512
    }
}
