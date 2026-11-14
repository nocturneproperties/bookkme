package com.bookk.appointments.data.orm.entity

import library.encryption.Decrypted
import library.encryption.valueOrNull
import kotlin.uuid.Uuid

internal data class ClientDetails(
    val fullName: String,
    val phone: String?,
    val email: String?,
    val note: String,
) {
    companion object {
        val ERASED = ClientDetails(fullName = "Deleted Client", phone = null, email = null, note = "")
        val UNREADABLE = ClientDetails(fullName = "", phone = null, email = null, note = "")
    }
}

internal const val ERASED_EMPLOYEE_NAME = "Deleted User"

internal class EncryptedClientDetails(
    val fullName: ByteArray,
    val phone: ByteArray?,
    val email: ByteArray?,
    val note: ByteArray,
    val nameIndex: String?,
)

internal class EncryptedSnapshot(
    val client: EncryptedClientDetails?,
    val employeeName: ByteArray?,
)

internal class StoredSnapshot(
    val clientId: Uuid,
    val employeeUserId: Uuid,
    val encryptedClient: EncryptedClientDetails?,
    val encryptedEmployeeName: ByteArray?,
)

internal class DecryptedSnapshot(
    private val decryptedClient: Decrypted<ClientDetails>,
    private val decryptedEmployeeName: Decrypted<String>,
) {
    val client: ClientDetails
        get() {
            return when (decryptedClient) {
                is Decrypted.Plain -> decryptedClient.value
                Decrypted.Shredded -> ClientDetails.ERASED
                Decrypted.Missing -> ClientDetails.UNREADABLE
            }
        }

    val readableClient: ClientDetails?
        get() {
            return decryptedClient.valueOrNull()
        }

    val employeeName: String
        get() {
            return when (decryptedEmployeeName) {
                is Decrypted.Plain -> decryptedEmployeeName.value
                Decrypted.Shredded -> ERASED_EMPLOYEE_NAME
                Decrypted.Missing -> ""
            }
        }
}
