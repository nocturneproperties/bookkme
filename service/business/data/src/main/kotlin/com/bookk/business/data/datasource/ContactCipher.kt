package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.Contact
import com.bookk.business.data.orm.entity.EncryptedContact
import com.bookk.business.data.orm.entity.StoredContact
import library.encryption.BlindIndex
import library.encryption.Decrypted
import library.encryption.Decryptor
import library.encryption.MissingKeys
import library.encryption.SubjectKeys
import library.encryption.decryptText
import library.encryption.encryptText
import library.encryption.ofCaseInsensitive
import library.encryption.valueOrNull
import kotlin.uuid.Uuid

internal class ContactCipher(
    private val subjectKeys: SubjectKeys,
    private val blindIndex: BlindIndex,
    private val missingKeys: MissingKeys,
) {

    suspend fun encrypt(subjectId: Uuid, contact: Contact): EncryptedContact? {
        val encryptor = subjectKeys.encryptor(subjectId) ?: return null
        return EncryptedContact(
            name = encryptor.encryptText(contact.name),
            lastName = encryptor.encryptText(contact.lastName),
            phone = contact.phone?.let(encryptor::encryptText),
            email = contact.email?.let(encryptor::encryptText),
            description = contact.description?.let(encryptor::encryptText),
        )
    }

    suspend fun decrypt(contact: StoredContact, source: String): Contact {
        return decryptAll(listOf(contact), source).single()
    }

    suspend fun decryptAll(contacts: List<StoredContact>, source: String): List<Contact> {
        val decryptor = subjectKeys.decryptor(contacts.map { it.subjectId }.distinct())
        return contacts.map { decryptor.decryptContact(it, source) }
    }

    fun emailIndex(email: String?): String? {
        return email?.let { blindIndex.ofCaseInsensitive(it) }
    }

    private fun Decryptor.decryptContact(stored: StoredContact, source: String): Contact {
        val subjectId = stored.subjectId
        val encrypted = stored.encrypted
        return when (val name = decryptText(subjectId, encrypted.name)) {
            is Decrypted.Plain -> Contact(
                name = name.value,
                lastName = textOf(subjectId, encrypted.lastName).orEmpty(),
                phone = textOf(subjectId, encrypted.phone),
                email = textOf(subjectId, encrypted.email),
                description = textOf(subjectId, encrypted.description),
            )

            Decrypted.Shredded -> Contact.UNREADABLE

            Decrypted.Missing -> Contact.UNREADABLE.also { missingKeys.report(source) }
        }
    }

    private fun Decryptor.textOf(subjectId: Uuid, ciphertext: ByteArray?): String? {
        return ciphertext?.let { decryptText(subjectId, it).valueOrNull() }
    }
}
