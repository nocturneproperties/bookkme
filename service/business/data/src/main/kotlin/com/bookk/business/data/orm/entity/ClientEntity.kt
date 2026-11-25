package com.bookk.business.data.orm.entity

import com.bookk.business.data.orm.table.ClientNameTokenTable
import com.bookk.business.data.orm.table.ClientTable
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.core.data.DecoratorUuidEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import kotlin.uuid.Uuid

internal class ClientEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    val businessId by ClientTable.businessId
    var userId by ClientTable.userId
    var nameEncrypted by ClientTable.nameEncrypted
    var lastNameEncrypted by ClientTable.lastNameEncrypted
    var phoneEncrypted by ClientTable.phoneEncrypted
    var emailEncrypted by ClientTable.emailEncrypted
    var descriptionEncrypted by ClientTable.descriptionEncrypted
    var emailIndex by ClientTable.emailIndex
    var phoneIndex by ClientTable.phoneIndex
    val createdAt by ClientTable.createdAt
    var sourceUpdatedAt by ClientTable.sourceUpdatedAt
    var lastActivityAt by ClientTable.lastActivityAt

    fun storedContact(): StoredContact {
        val encrypted = EncryptedContact(
            name = nameEncrypted,
            lastName = lastNameEncrypted,
            phone = phoneEncrypted,
            email = emailEncrypted,
            description = descriptionEncrypted,
        )
        return StoredContact(subjectId = id.value, encrypted = encrypted)
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
                createdAt = createdAt,
            )

            else -> Client.Integrated(
                id = id.value,
                name = contact.name,
                lastName = contact.lastName,
                phone = contact.phone,
                email = contact.email,
                userId = userId,
                description = contact.description,
                createdAt = createdAt,
            )
        }
    }

    companion object : DecoratorUuidEntityClass<ClientEntity>(ClientTable) {
        fun UpdateBuilder<*>.writeContact(contact: EncryptedContact, indexes: ContactIndexes) {
            this[ClientTable.nameEncrypted] = contact.name
            this[ClientTable.lastNameEncrypted] = contact.lastName
            this[ClientTable.phoneEncrypted] = contact.phone
            this[ClientTable.emailEncrypted] = contact.email
            this[ClientTable.descriptionEncrypted] = contact.description
            this[ClientTable.emailIndex] = indexes.email
            this[ClientTable.phoneIndex] = indexes.phone
        }

        fun ClientEntity.storeContact(contact: EncryptedContact, indexes: ContactIndexes, reindex: ContactReindex) {
            nameEncrypted = contact.name
            lastNameEncrypted = contact.lastName
            phoneEncrypted = contact.phone
            emailEncrypted = contact.email
            descriptionEncrypted = contact.description
            if (reindex.email) emailIndex = indexes.email
            if (reindex.phone) phoneIndex = indexes.phone
            if (reindex.name) replaceNameTokens(indexes.nameTokens)
        }

        fun insertNameTokens(businessId: Uuid, tokensByClient: Map<Uuid, List<ByteArray>>) {
            val rows = tokensByClient.flatMap { (clientId, tokens) -> tokens.map { clientId to it } }
            ClientNameTokenTable.batchInsert(rows, shouldReturnGeneratedValues = false) { (clientId, token) ->
                this[ClientNameTokenTable.businessId] = businessId
                this[ClientNameTokenTable.token] = token
                this[ClientNameTokenTable.clientId] = clientId
            }
        }

        fun findByIdAndUpdateSearchIndexes(id: Uuid, nameTokens: List<ByteArray>, phoneIndex: String?): ClientEntity? {
            val entity = findByIdAndUpdate(id) { phoneIndex?.let { index -> it.phoneIndex = index } } ?: return null
            if (nameTokens.isNotEmpty()) entity.replaceNameTokens(nameTokens)
            return entity
        }

        private fun ClientEntity.replaceNameTokens(tokens: List<ByteArray>) {
            val clientId = id.value
            ClientNameTokenTable.deleteWhere { ClientNameTokenTable.clientId eq clientId }
            insertNameTokens(businessId.value, mapOf(clientId to tokens))
        }
    }
}
