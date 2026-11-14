package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.Contact
import com.bookk.business.data.orm.entity.EncryptedContact
import library.encryption.BlindIndex
import library.encryption.Decrypted
import library.encryption.Decryptor
import library.encryption.Encryptor
import library.encryption.MissingKeys
import library.encryption.SubjectKeys
import library.encryption.ofCaseInsensitive
import kotlin.uuid.Uuid

internal class ContactCipher(
    private val subjectKeys: SubjectKeys,
    private val blindIndex: BlindIndex,
    private val missingKeys: MissingKeys,
) {

    suspend fun encrypt(subjectId: Uuid, contact: Contact): EncryptedContact? {
        val encryptor = subjectKeys.encryptor(subjectId) ?: return null
        return EncryptedContact(
            name = encryptor.bytesOf(contact.name),
            lastName = encryptor.bytesOf(contact.lastName),
            phone = contact.phone?.let { encryptor.bytesOf(it) },
            email = contact.email?.let { encryptor.bytesOf(it) },
            description = contact.description?.let { encryptor.bytesOf(it) },
        )
    }

    fun emailIndex(email: String?): String? {
        return email?.let { blindIndex.ofCaseInsensitive(it) }
    }

    suspend fun reader(subjectIds: Collection<Uuid>, source: String): ContactReader {
        return ContactReader(subjectKeys.decryptor(subjectIds.distinct()), missingKeys, source)
    }

    private fun Encryptor.bytesOf(text: String): ByteArray {
        return encrypt(text.encodeToByteArray())
    }
}

internal class ContactReader(
    private val decryptor: Decryptor,
    private val missingKeys: MissingKeys,
    private val source: String,
) {

    fun read(subjectId: Uuid, encrypted: EncryptedContact): Contact {
        val name = decryptor.decrypt(subjectId, encrypted.name)
        if (name !is Decrypted.Plain) return unreadable(name)
        return Contact(
            name = name.value.decodeToString(),
            lastName = textOf(subjectId, encrypted.lastName).orEmpty(),
            phone = textOf(subjectId, encrypted.phone),
            email = textOf(subjectId, encrypted.email),
            description = textOf(subjectId, encrypted.description),
        )
    }

    private fun unreadable(outcome: Decrypted<ByteArray>): Contact {
        if (outcome is Decrypted.Missing) missingKeys.report(source)
        return Contact.UNREADABLE
    }

    private fun textOf(subjectId: Uuid, ciphertext: ByteArray?): String? {
        val decrypted = ciphertext?.let { decryptor.decrypt(subjectId, it) }
        return (decrypted as? Decrypted.Plain)?.value?.decodeToString()
    }
}
