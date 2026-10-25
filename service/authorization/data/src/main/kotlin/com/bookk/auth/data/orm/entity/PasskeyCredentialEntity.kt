package com.bookk.auth.data.orm.entity

import com.bookk.auth.data.orm.table.PasskeyCredentialTable
import com.bookk.core.data.DecoratorUuidEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.uuid.Uuid

internal class PasskeyCredentialEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var authorization by AuthenticationEntity referencedOn PasskeyCredentialTable.authId
    var credDescriptorId by PasskeyCredentialTable.credDescriptorId
    var credDescriptorType by PasskeyCredentialTable.credDescriptorType
    var credDescriptorTransports by PasskeyCredentialTable.credDescriptorTransports
    var name by PasskeyCredentialTable.name
    var publicKey by PasskeyCredentialTable.publicKey
    var signatureCount by PasskeyCredentialTable.signatureCount
    var isDiscoverable by PasskeyCredentialTable.isDiscoverable
    var isBackupEligible by PasskeyCredentialTable.isBackupEligible
    var isBackedUp by PasskeyCredentialTable.isBackedUp
    var attestationObject by PasskeyCredentialTable.attestationObject
    var clientData by PasskeyCredentialTable.clientDataJson
    var createdAt by PasskeyCredentialTable.createdAt
    var updatedAt by PasskeyCredentialTable.updatedAt
    var lastUsedAt by PasskeyCredentialTable.lastUsedAt

    companion object : DecoratorUuidEntityClass<PasskeyCredentialEntity>(PasskeyCredentialTable)
}