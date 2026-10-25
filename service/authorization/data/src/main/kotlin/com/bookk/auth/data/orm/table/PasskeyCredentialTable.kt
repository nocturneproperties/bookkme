package com.bookk.auth.data.orm.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

internal object PasskeyCredentialTable: UuidTable("passkey_credentials") {
    val authId = reference("auth_id", AuthenticationTable, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val credDescriptorId = binary("cred_descriptor_id", 24)
    val credDescriptorType = varchar("cred_descriptor_type", 255)
    val credDescriptorTransports = varchar("cred_descriptor_transports", 255)
    val publicKey = text("public_key")
    val signatureCount = long("signature_count")
    val isDiscoverable = bool("discoverable")
    val isBackupEligible = bool("backup_eligible")
    val isBackedUp = bool("backed_up")
    val attestationObject = binary("attestation_object", 255)
    val clientDataJson = varchar("client_data", 512)
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
    val lastUsedAt = timestamp("last_used_at").clientDefault { Clock.System.now() }

    init {
        uniqueIndex(authId, credDescriptorId)
    }
}