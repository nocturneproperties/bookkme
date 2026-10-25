package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientRemote
import com.bookk.business.domain.api.client.entity.ClientUpdateModel
import com.bookk.business.domain.api.client.entity.toRemote
import com.bookk.business.domain.api.client.operation.UpdateClient
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.permissions.PermissionAction
import library.permissions.assert
import library.validation.EmailValidator
import library.validation.NameValidator
import library.validation.PhoneValidator
import kotlin.uuid.Uuid

internal class UpdateClientImpl(
    private val transactionManager: TransactionManager,
    private val clientDataSource: ClientDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource
) : UpdateClient {
    override suspend fun invoke(requestUserId: Uuid, businessId: Uuid, model: ClientUpdateModel): Result<ClientRemote> =
        transactionManager.transaction {
            businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS)
                .assert(PermissionAction.UPDATE)
            val existing = clientDataSource.getClientById(businessId, model.id) ?: throw UpdateClient.Error.NotFound()

            val isNameChange = model.name != null && model.name != existing.name
            val isLastNameChange = model.lastName != null && model.lastName != existing.lastName
            val isPhoneChange = model.phone != null && model.phone != existing.phone
            val isEmailChange = model.email != null && model.email != existing.email

            val personalInfoRequested = isNameChange || isLastNameChange || isPhoneChange || isEmailChange
            if (existing is Client.Integrated && personalInfoRequested) {
                throw UpdateClient.Error.PersonalInfoNotEditable()
            }

            val name = model.name?.also { name ->
                if (!NameValidator.isValid(name, minLength = 0, maxLength = MAX_NAME_LENGTH)) {
                    throw UpdateClient.Error.ClientValidationError()
                }
            }
            val lastName = model.lastName?.also { lastName ->
                if (!NameValidator.isValid(lastName, minLength = 0, maxLength = MAX_NAME_LENGTH)) {
                    throw UpdateClient.Error.ClientValidationError()
                }
            }
            val phone = model.phone?.takeIf { it.isNotBlank() }?.also { phone ->
                if (!PhoneValidator.isValid(phone)) throw UpdateClient.Error.ClientValidationError()
                val conflict = clientDataSource.getClient(businessId, phone)
                if (conflict != null && conflict.id != model.id) throw UpdateClient.Error.ClientExist()
            }
            val email = model.email?.takeIf { it.isNotBlank() }?.also { email ->
                if (!EmailValidator.isValid(email)) throw UpdateClient.Error.ClientValidationError()
            }

            val trimmedModel = model.copy(
                name = name?.trim(),
                lastName = lastName?.trim(),
                phone = phone?.trim(),
                email = email?.trim(),
                description = model.description?.trim()?.take(Client.MAX_DESCRIPTION_LENGTH)
            )

            (clientDataSource.updateClient(businessId, trimmedModel) ?: throw UpdateClient.Error.NotFound()).toRemote()
        }

    companion object {
        const val MAX_NAME_LENGTH = 512
    }
}
