package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.entity.ClientDetails
import com.bookk.appointments.data.orm.entity.ERASED_EMPLOYEE_NAME
import com.bookk.appointments.data.orm.entity.EncryptedClientDetails
import com.bookk.appointments.data.orm.entity.EncryptedSnapshot
import com.bookk.appointments.data.orm.entity.StoredSnapshot
import library.encryption.Decrypted
import library.encryption.Decryptor
import library.encryption.Encryptor
import library.encryption.MissingKeys
import library.encryption.SubjectKeys
import kotlin.uuid.Uuid

internal class SnapshotCipher(
    private val subjectKeys: SubjectKeys,
    private val missingKeys: MissingKeys,
) {

    suspend fun encrypt(
        clientId: Uuid,
        client: ClientDetails,
        employeeUserId: Uuid,
        employeeName: String,
    ): EncryptedSnapshot {
        return EncryptedSnapshot(
            client = encryptClient(clientId, client),
            employeeName = encryptEmployee(employeeUserId, employeeName),
        )
    }

    private suspend fun encryptClient(clientId: Uuid, client: ClientDetails): EncryptedClientDetails? {
        val encryptor = subjectKeys.encryptor(clientId) ?: return null
        return EncryptedClientDetails(
            fullName = encryptor.bytesOf(client.fullName),
            phone = client.phone?.let { encryptor.bytesOf(it) },
            email = client.email?.let { encryptor.bytesOf(it) },
            note = encryptor.bytesOf(client.note),
        )
    }

    private suspend fun encryptEmployee(employeeUserId: Uuid, name: String): ByteArray? {
        if (employeeUserId == Uuid.NIL) return null
        return subjectKeys.encryptor(employeeUserId)?.bytesOf(name)
    }

    suspend fun encryptPlaintext(stored: StoredSnapshot): EncryptedSnapshot {
        val client = stored.plaintextClient
        val employeeName = stored.plaintextEmployeeName
        return EncryptedSnapshot(
            client = if (client != null) encryptClient(stored.clientId, client) else stored.encryptedClient,
            employeeName = if (employeeName != null) {
                encryptEmployee(stored.employeeUserId, employeeName)
            } else {
                stored.encryptedEmployeeName
            },
        )
    }

    suspend fun reencryptClient(
        stored: StoredSnapshot,
        reader: SnapshotReader,
        toClientId: Uuid,
    ): EncryptedClientDetails? {
        return reader.readableClient(stored)?.let { encryptClient(toClientId, it) }
    }

    suspend fun reader(snapshots: Collection<StoredSnapshot>, source: String): SnapshotReader {
        val subjectIds = snapshots.flatMap { listOf(it.clientId, it.employeeUserId) }.distinct()
        return SnapshotReader(subjectKeys.decryptor(subjectIds), missingKeys, source)
    }

    private fun Encryptor.bytesOf(text: String): ByteArray {
        return encrypt(text.encodeToByteArray())
    }
}

internal class SnapshotReader(
    private val decryptor: Decryptor,
    private val missingKeys: MissingKeys,
    private val source: String,
) {

    fun client(snapshot: StoredSnapshot): ClientDetails {
        return when (val client = decryptClient(snapshot)) {
            is Decrypted.Plain -> client.value
            Decrypted.Shredded -> ClientDetails.ERASED
            Decrypted.Missing -> reportMissing(ClientDetails.UNREADABLE)
        }
    }

    fun readableClient(snapshot: StoredSnapshot): ClientDetails? {
        return when (val client = decryptClient(snapshot)) {
            is Decrypted.Plain -> client.value
            Decrypted.Shredded -> null
            Decrypted.Missing -> reportMissing(null)
        }
    }

    private fun decryptClient(snapshot: StoredSnapshot): Decrypted<ClientDetails> {
        val encrypted = snapshot.encryptedClient ?: return plaintextClient(snapshot)
        val clientId = snapshot.clientId
        return when (val name = decryptor.decrypt(clientId, encrypted.fullName)) {
            is Decrypted.Plain -> Decrypted.Plain(
                ClientDetails(
                    fullName = name.value.decodeToString(),
                    phone = textOf(clientId, encrypted.phone),
                    email = textOf(clientId, encrypted.email),
                    note = textOf(clientId, encrypted.note).orEmpty(),
                ),
            )

            Decrypted.Shredded -> Decrypted.Shredded

            Decrypted.Missing -> Decrypted.Missing
        }
    }

    private fun plaintextClient(snapshot: StoredSnapshot): Decrypted<ClientDetails> {
        val plaintext = snapshot.plaintextClient ?: return Decrypted.Shredded
        return Decrypted.Plain(plaintext)
    }

    fun employeeName(snapshot: StoredSnapshot): String {
        val encrypted = snapshot.encryptedEmployeeName ?: return snapshot.plaintextEmployeeName ?: ERASED_EMPLOYEE_NAME
        return when (val name = decryptor.decrypt(snapshot.employeeUserId, encrypted)) {
            is Decrypted.Plain -> name.value.decodeToString()
            Decrypted.Shredded -> ERASED_EMPLOYEE_NAME
            Decrypted.Missing -> reportMissing("")
        }
    }

    private fun <T> reportMissing(unreadable: T): T {
        missingKeys.report(source)
        return unreadable
    }

    private fun textOf(subjectId: Uuid, ciphertext: ByteArray?): String? {
        val decrypted = ciphertext?.let { decryptor.decrypt(subjectId, it) }
        return (decrypted as? Decrypted.Plain)?.value?.decodeToString()
    }
}
