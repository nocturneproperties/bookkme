package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.entity.ClientDetails
import com.bookk.appointments.data.orm.entity.DecryptedSnapshot
import com.bookk.appointments.data.orm.entity.EncryptedClientDetails
import com.bookk.appointments.data.orm.entity.EncryptedSnapshot
import com.bookk.appointments.data.orm.entity.StoredSnapshot
import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import library.encryption.BlindIndex
import library.encryption.Decrypted
import library.encryption.Decryptor
import library.encryption.MissingKeys
import library.encryption.SubjectKeys
import library.encryption.decryptText
import library.encryption.encryptText
import library.encryption.map
import library.encryption.ofCaseInsensitive
import library.encryption.valueOrNull
import kotlin.uuid.Uuid

internal class SnapshotCipher(
    private val subjectKeys: SubjectKeys,
    private val blindIndex: BlindIndex,
    private val missingKeys: MissingKeys,
) {

    suspend fun encrypt(appointment: Appointment): EncryptedSnapshot {
        val client = appointment.client
        val details = ClientDetails(client.fullName, client.phone, client.email, note = appointment.note)
        return encrypt(client.id, details, appointment.employee)
    }

    suspend fun encrypt(request: AppointmentRequest): EncryptedSnapshot {
        val client = request.client
        val details = ClientDetails(client.fullName, client.phone, client.email, note = request.note)
        return encrypt(client.id, details, request.employee)
    }

    suspend fun decrypt(snapshot: StoredSnapshot, source: String): DecryptedSnapshot {
        return decryptAll(listOf(snapshot), source).single()
    }

    suspend fun decryptAll(snapshots: List<StoredSnapshot>, source: String): List<DecryptedSnapshot> {
        val subjectIds = snapshots.flatMap { listOf(it.clientId, it.employeeUserId) }.distinct()
        val decryptor = subjectKeys.decryptor(subjectIds)
        return snapshots.map { snapshot ->
            val client = decryptor.decryptClient(snapshot)
            val employeeName = decryptor.decryptEmployeeName(snapshot)
            if (client is Decrypted.Missing) missingKeys.report(source)
            if (employeeName is Decrypted.Missing) missingKeys.report(source)
            DecryptedSnapshot(client, employeeName)
        }
    }

    suspend fun clientNameIndexes(snapshots: List<StoredSnapshot>): List<String?> {
        val decryptor = subjectKeys.decryptor(snapshots.map { it.clientId }.distinct())
        return snapshots.map { snapshot ->
            decryptor.decryptClient(snapshot).valueOrNull()?.let { clientNameIndex(it.fullName) }
        }
    }

    fun clientNameIndex(fullName: String): String? {
        return fullName.takeIf { it.isNotBlank() }?.let(blindIndex::ofCaseInsensitive)
    }

    suspend fun reencryptClient(snapshot: DecryptedSnapshot, toClientId: Uuid): EncryptedClientDetails? {
        return snapshot.readableClient?.let { encryptClient(toClientId, it) }
    }

    private suspend fun encrypt(clientId: Uuid, client: ClientDetails, employee: EmployeeSnapshot): EncryptedSnapshot {
        return EncryptedSnapshot(client = encryptClient(clientId, client), employeeName = encryptEmployeeName(employee))
    }

    private suspend fun encryptClient(clientId: Uuid, client: ClientDetails): EncryptedClientDetails? {
        val encryptor = subjectKeys.encryptor(clientId) ?: return null
        return EncryptedClientDetails(
            fullName = encryptor.encryptText(client.fullName),
            phone = client.phone?.let(encryptor::encryptText),
            email = client.email?.let(encryptor::encryptText),
            note = encryptor.encryptText(client.note),
            nameIndex = clientNameIndex(client.fullName),
        )
    }

    private suspend fun encryptEmployeeName(employee: EmployeeSnapshot): ByteArray? {
        if (employee.userId == Uuid.NIL) return null
        return subjectKeys.encryptor(employee.userId)?.encryptText(employee.fullName)
    }

    private fun Decryptor.decryptClient(snapshot: StoredSnapshot): Decrypted<ClientDetails> {
        val encrypted = snapshot.encryptedClient ?: return Decrypted.Shredded
        val clientId = snapshot.clientId
        return decryptText(clientId, encrypted.fullName).map { fullName ->
            ClientDetails(
                fullName = fullName,
                phone = textOf(clientId, encrypted.phone),
                email = textOf(clientId, encrypted.email),
                note = textOf(clientId, encrypted.note).orEmpty(),
            )
        }
    }

    private fun Decryptor.decryptEmployeeName(snapshot: StoredSnapshot): Decrypted<String> {
        val encrypted = snapshot.encryptedEmployeeName ?: return Decrypted.Shredded
        return decryptText(snapshot.employeeUserId, encrypted)
    }

    private fun Decryptor.textOf(subjectId: Uuid, ciphertext: ByteArray?): String? {
        return ciphertext?.let { decryptText(subjectId, it).valueOrNull() }
    }
}
