package com.bookk.auth.domain.api.identification.entity

import com.bookk.auth.domain.api.authentication.entity.Authentication
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Entity is immutable and byte arrays expected to never be touched
 * so saving memory by avoiding deep copying arrays is desired behaviour
 */
@Suppress("ArrayInDataClass")
data class PasskeyCredential(
    val id: Uuid,
    val authId: Uuid,
    val authInfo: Authentication,
    val handle: Uuid,
    val name: String,
    val credDescriptor: CredentialDescriptor,
    val publicKey: String,
    val signatureCount: Long,
    val isDiscoverable: Boolean,
    val isBackupEligible: Boolean,
    val isBackedUp: Boolean,
    val attestationObject: ByteArray,
    val clientData: String,
    val createdAt: Instant,
    val lastUsedAt: Instant
) {

    /**
     * @see <a href="https://www.w3.org/TR/webauthn-2/#dictionary-credential-descriptor"</a>
     * */
    class CredentialDescriptor(
        val id: ByteArray,
        val type: String,
        val transports: Set<String>
    )

    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            authInfo: Authentication = Authentication.stub(),
            name: String = "My key",
            credDescriptor: CredentialDescriptor = CredentialDescriptor(
                id = ByteArray(24) { it.toByte() },
                type = "public-key",
                transports = setOf("internal")
            ),
            publicKey: String = "base64pubkey==",
            signatureCount: Long = 0L,
            isDiscoverable: Boolean = true,
            isBackupEligible: Boolean = false,
            isBackedUp: Boolean = false,
            attestationObject: ByteArray = ByteArray(32) { 0 },
            clientData: String = "{}"
        ) = PasskeyCredential(
            id = id,
            authId = authInfo.id,
            authInfo = authInfo,
            handle = authInfo.uuid,
            name = name,
            credDescriptor = credDescriptor,
            publicKey = publicKey,
            signatureCount = signatureCount,
            isDiscoverable = isDiscoverable,
            isBackupEligible = isBackupEligible,
            isBackedUp = isBackedUp,
            attestationObject = attestationObject,
            clientData = clientData,
            createdAt = Instant.fromEpochMilliseconds(0),
            lastUsedAt = Instant.fromEpochMilliseconds(0)
        )
    }
}