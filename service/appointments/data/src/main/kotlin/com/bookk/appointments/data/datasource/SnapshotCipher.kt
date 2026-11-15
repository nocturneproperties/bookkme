package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.entity.ClientDetails
import com.bookk.appointments.data.orm.entity.DecryptedClient
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
import library.encryption.Encryptor
import library.encryption.MissingKeys
import library.encryption.SubjectKeys
import library.encryption.decryptText
import library.encryption.encryptText
import library.encryption.ofCaseInsensitive
import library.encryption.valueOrNull
import kotlin.uuid.Uuid

internal class SnapshotCipher(
    private val subjectKeys: SubjectKeys,
    private val blindIndex: BlindIndex,
    private val missingKeys: MissingKeys,
) {

    suspend fun encrypt(appointment: Appointment): EncryptedSnapshot {
        return encrypt(appointment.client.id, appointment.clientDetails(), appointment.employee)
    }

    suspend fun encrypt(request: AppointmentRequest): EncryptedSnapshot {
        return encrypt(request.client.id, request.clientDetails(), request.employee)
    }

    suspend fun encryptChanges(appointment: Appointment, read: ReadSnapshot): EncryptedSnapshot {
        return encryptChanges(appointment.client.id, appointment.clientDetails(), appointment.employee, read)
    }

    suspend fun encryptChanges(request: AppointmentRequest, read: ReadSnapshot): EncryptedSnapshot {
        return encryptChanges(request.client.id, request.clientDetails(), request.employee, read)
    }

    suspend fun read(snapshot: StoredSnapshot, source: String): ReadSnapshot {
        return ReadSnapshot(stored = snapshot, decrypted = decrypt(snapshot, source))
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
            if (client.hasMissingKey) missingKeys.report(source)
            if (employeeName is Decrypted.Missing) missingKeys.report(source)
            DecryptedSnapshot(client, employeeName)
        }
    }

    suspend fun clientNameIndexes(snapshots: List<StoredSnapshot>): List<String?> {
        val decryptor = subjectKeys.decryptor(snapshots.map { it.clientId }.distinct())
        return snapshots.map { snapshot ->
            val client = decryptor.decryptClient(snapshot)
            client.details.fullName.takeIf { client.isNameReadable }?.let(::clientNameIndex)
        }
    }

    fun clientNameIndex(fullName: String): String? {
        return fullName.takeIf { it.isNotBlank() }?.let(blindIndex::ofCaseInsensitive)
    }

    suspend fun reencryptClients(
        snapshots: List<DecryptedSnapshot>,
        toClientId: Uuid,
    ): List<EncryptedClientDetails?> {
        val readable = snapshots.map { it.readableClient }
        if (readable.all { it == null }) return readable.map { null }
        val encryptor = subjectKeys.encryptor(toClientId) ?: return readable.map { null }
        return readable.map { client -> client?.let { encryptor.encryptClient(it) } }
    }

    private suspend fun encrypt(clientId: Uuid, client: ClientDetails, employee: EmployeeSnapshot): EncryptedSnapshot {
        return EncryptedSnapshot(
            client = subjectKeys.encryptor(clientId)?.encryptClient(client),
            employeeName = encryptEmployeeName(employee),
        )
    }

    private suspend fun encryptChanges(
        clientId: Uuid,
        client: ClientDetails,
        employee: EmployeeSnapshot,
        read: ReadSnapshot,
    ): EncryptedSnapshot {
        return EncryptedSnapshot(
            client = encryptClientChanges(clientId, client, read),
            employeeName = encryptEmployeeNameChanges(employee, read),
        )
    }

    private suspend fun encryptClientChanges(
        clientId: Uuid,
        edited: ClientDetails,
        read: ReadSnapshot,
    ): EncryptedClientDetails? {
        val stored = read.stored.encryptedClient
        if (stored == null || read.decrypted.isClientErased) return null
        if (clientId != read.stored.clientId) return subjectKeys.encryptor(clientId)?.encryptClient(edited)
        val current = read.decrypted.client
        if (edited == current) return stored
        val encryptor = subjectKeys.encryptor(clientId) ?: return null
        val nameKept = edited.fullName == current.fullName
        return EncryptedClientDetails(
            fullName = if (nameKept) stored.fullName else encryptor.encryptText(edited.fullName),
            phone = encryptor.changed(stored.phone, current.phone, edited.phone),
            email = encryptor.changed(stored.email, current.email, edited.email),
            note = if (edited.note == current.note) stored.note else encryptor.encryptText(edited.note),
            nameIndex = if (nameKept) stored.nameIndex else clientNameIndex(edited.fullName),
        )
    }

    private suspend fun encryptEmployeeNameChanges(employee: EmployeeSnapshot, read: ReadSnapshot): ByteArray? {
        val stored = read.stored.encryptedEmployeeName
        val isSameEmployee = employee.userId == read.stored.employeeUserId
        val isUnchanged = isSameEmployee && employee.fullName == read.decrypted.employeeName
        if (stored != null && isUnchanged && !read.decrypted.isEmployeeNameErased) return stored
        return encryptEmployeeName(employee)
    }

    private suspend fun encryptEmployeeName(employee: EmployeeSnapshot): ByteArray? {
        if (employee.userId == Uuid.NIL) return null
        return subjectKeys.encryptor(employee.userId)?.encryptText(employee.fullName)
    }

    private fun Encryptor.encryptClient(client: ClientDetails): EncryptedClientDetails {
        return EncryptedClientDetails(
            fullName = encryptText(client.fullName),
            phone = client.phone?.let(::encryptText),
            email = client.email?.let(::encryptText),
            note = encryptText(client.note),
            nameIndex = clientNameIndex(client.fullName),
        )
    }

    private fun Encryptor.changed(stored: ByteArray?, current: String?, edited: String?): ByteArray? {
        return if (edited == current) stored else edited?.let(::encryptText)
    }

    private fun Decryptor.decryptClient(snapshot: StoredSnapshot): DecryptedClient {
        val encrypted = snapshot.encryptedClient ?: return DecryptedClient.ERASED
        val clientId = snapshot.clientId
        val fullName = decryptText(clientId, encrypted.fullName)
        if (fullName is Decrypted.Shredded) return DecryptedClient.ERASED
        val phone = encrypted.phone?.let { decryptText(clientId, it) }
        val email = encrypted.email?.let { decryptText(clientId, it) }
        val note = decryptText(clientId, encrypted.note)
        val details = ClientDetails(
            fullName = fullName.valueOrNull().orEmpty(),
            phone = phone?.valueOrNull(),
            email = email?.valueOrNull(),
            note = note.valueOrNull().orEmpty(),
        )
        return DecryptedClient(
            details = details,
            isNameReadable = fullName is Decrypted.Plain,
            isErased = false,
            hasMissingKey = listOfNotNull(fullName, phone, email, note).any { it is Decrypted.Missing },
        )
    }

    private fun Decryptor.decryptEmployeeName(snapshot: StoredSnapshot): Decrypted<String> {
        val encrypted = snapshot.encryptedEmployeeName ?: return Decrypted.Shredded
        return decryptText(snapshot.employeeUserId, encrypted)
    }

    private fun Appointment.clientDetails(): ClientDetails {
        return ClientDetails(client.fullName, client.phone, client.email, note = note)
    }

    private fun AppointmentRequest.clientDetails(): ClientDetails {
        return ClientDetails(client.fullName, client.phone, client.email, note = note)
    }
}

internal class ReadSnapshot(val stored: StoredSnapshot, val decrypted: DecryptedSnapshot)
