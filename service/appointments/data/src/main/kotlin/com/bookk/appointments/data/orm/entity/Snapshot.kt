package com.bookk.appointments.data.orm.entity

import library.encryption.Decrypted
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

internal class DecryptedClient(
    val details: ClientDetails,
    val isNameReadable: Boolean,
    val isErased: Boolean,
    val hasMissingKey: Boolean,
) {
    companion object {
        val ERASED = DecryptedClient(
            details = ClientDetails.ERASED,
            isNameReadable = false,
            isErased = true,
            hasMissingKey = false,
        )
    }
}

internal class DecryptedSnapshot(
    private val decryptedClient: DecryptedClient,
    private val decryptedEmployeeName: Decrypted<String>,
) {
    val client: ClientDetails
        get() {
            return decryptedClient.details
        }

    val readableClient: ClientDetails?
        get() {
            return decryptedClient.details.takeIf { decryptedClient.isNameReadable }
        }

    val isClientErased: Boolean
        get() {
            return decryptedClient.isErased
        }

    val isEmployeeNameErased: Boolean
        get() {
            return decryptedEmployeeName is Decrypted.Shredded
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
