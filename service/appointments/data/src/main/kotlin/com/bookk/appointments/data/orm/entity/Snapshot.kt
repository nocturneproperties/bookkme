package com.bookk.appointments.data.orm.entity

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
)

internal class EncryptedSnapshot(
    val client: EncryptedClientDetails?,
    val employeeName: ByteArray?,
)

internal class StoredSnapshot(
    val clientId: Uuid,
    val employeeUserId: Uuid,
    val plaintextClient: ClientDetails?,
    val encryptedClient: EncryptedClientDetails?,
    val plaintextEmployeeName: String?,
    val encryptedEmployeeName: ByteArray?,
)
