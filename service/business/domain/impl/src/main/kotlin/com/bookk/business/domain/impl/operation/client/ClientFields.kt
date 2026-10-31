package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.operation.CreateClient
import library.validation.EmailValidator
import library.validation.NameValidator
import library.validation.PhoneValidator

internal class ClientFields(
    val name: String,
    val lastName: String,
    val phone: String?,
    val email: String?,
    val description: String?
)

internal const val MAX_CLIENT_NAME_LENGTH = 512

internal fun Client.validatedFields(): ClientFields {
    if (!NameValidator.isValid(name, minLength = 0, maxLength = MAX_CLIENT_NAME_LENGTH)) {
        throw CreateClient.Error.ClientValidationError()
    }
    if (!NameValidator.isValid(lastName, minLength = 0, maxLength = MAX_CLIENT_NAME_LENGTH)) {
        throw CreateClient.Error.ClientValidationError()
    }
    val validPhone = phone?.takeIf { it.isNotBlank() }?.also { phone ->
        if (!PhoneValidator.isValid(phone)) throw CreateClient.Error.ClientValidationError()
    }
    val validEmail = email?.takeIf { it.isNotBlank() }?.also { email ->
        if (!EmailValidator.isValid(email)) throw CreateClient.Error.ClientValidationError()
    }
    if (validPhone == null && validEmail == null) throw CreateClient.Error.MissingContactInfo()

    return ClientFields(
        name = name.trim(),
        lastName = lastName.trim(),
        phone = validPhone?.trim(),
        email = validEmail?.trim(),
        description = description?.trim()?.take(Client.MAX_DESCRIPTION_LENGTH)
    )
}
