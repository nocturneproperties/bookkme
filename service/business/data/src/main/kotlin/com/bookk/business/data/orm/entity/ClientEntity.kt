package com.bookk.business.data.orm.entity

import com.bookk.business.data.datasource.ContactReader
import com.bookk.business.data.orm.table.ClientTable
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.core.data.DecoratorUuidEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.uuid.Uuid

internal class ClientEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    val businessId by ClientTable.businessId
    var name by ClientTable.name
    var lastName by ClientTable.lastName
    var phone by ClientTable.phone
    var email by ClientTable.email
    var userId by ClientTable.userId
    var description by ClientTable.description
    var nameEncrypted by ClientTable.nameEncrypted
    var lastNameEncrypted by ClientTable.lastNameEncrypted
    var phoneEncrypted by ClientTable.phoneEncrypted
    var emailEncrypted by ClientTable.emailEncrypted
    var descriptionEncrypted by ClientTable.descriptionEncrypted
    var emailIndex by ClientTable.emailIndex
    var sourceUpdatedAt by ClientTable.sourceUpdatedAt
    var lastActivityAt by ClientTable.lastActivityAt

    fun plaintextContact(): Contact {
        return Contact(
            name = name.orEmpty(),
            lastName = lastName.orEmpty(),
            phone = phone,
            email = email,
            description = description,
        )
    }

    fun contact(reader: ContactReader): Contact {
        val encryptedName = nameEncrypted ?: return plaintextContact()
        val encrypted = EncryptedContact(
            name = encryptedName,
            lastName = checkNotNull(lastNameEncrypted) { "Client ${id.value} has no encrypted last name" },
            phone = phoneEncrypted,
            email = emailEncrypted,
            description = descriptionEncrypted,
        )
        return reader.read(id.value, encrypted)
    }

    fun store(contact: EncryptedContact, emailIndex: String?, reindexEmail: Boolean) {
        val unindexed = nameEncrypted == null
        nameEncrypted = contact.name
        lastNameEncrypted = contact.lastName
        phoneEncrypted = contact.phone
        emailEncrypted = contact.email
        descriptionEncrypted = contact.description
        name = null
        lastName = null
        phone = null
        email = null
        description = null
        if (unindexed || reindexEmail) this.emailIndex = emailIndex
    }

    fun toDomain(contact: Contact): Client {
        return when (val userId = userId) {
            null -> Client.Detached(
                id = id.value,
                name = contact.name,
                lastName = contact.lastName,
                phone = contact.phone,
                email = contact.email,
                description = contact.description,
            )

            else -> Client.Integrated(
                id = id.value,
                name = contact.name,
                lastName = contact.lastName,
                phone = contact.phone,
                email = contact.email,
                userId = userId,
                description = contact.description,
            )
        }
    }

    companion object : DecoratorUuidEntityClass<ClientEntity>(ClientTable) {
        fun UpdateBuilder<*>.writeContact(contact: EncryptedContact, emailIndex: String?) {
            this[ClientTable.nameEncrypted] = contact.name
            this[ClientTable.lastNameEncrypted] = contact.lastName
            this[ClientTable.phoneEncrypted] = contact.phone
            this[ClientTable.emailEncrypted] = contact.email
            this[ClientTable.descriptionEncrypted] = contact.description
            this[ClientTable.emailIndex] = emailIndex
        }
    }
}
