package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.Contact
import com.bookk.business.data.orm.entity.ContactIndexes
import com.bookk.business.data.orm.entity.EncryptedContact
import com.bookk.business.data.orm.entity.StoredContact
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

    suspend fun encryptChanges(read: ReadContact, edited: Contact): EncryptedContact? {
        return encryptChanges(read.stored.subjectId, listOf(ContactEdit(read, edited)))?.single()
    }

    suspend fun encryptChanges(subjectId: Uuid, edits: List<ContactEdit>): List<EncryptedContact>? {
        require(edits.all { it.read.stored.subjectId == subjectId }) { "Every edit must be of subject $subjectId" }
        val encryptor = subjectKeys.encryptor(subjectId) ?: return null
        return edits.map { encryptor.changes(it.read, it.edited) }
    }

    suspend fun decrypt(contact: StoredContact, source: String): Contact {
        return read(contact, source).contact
    }

    suspend fun decryptAll(contacts: List<StoredContact>, source: String): List<Contact> {
        return readAll(contacts, source).map { it.contact }
    }

    suspend fun read(contact: StoredContact, source: String): ReadContact {
        return readAll(listOf(contact), source).single()
    }

    suspend fun readAll(contacts: List<StoredContact>, source: String): List<ReadContact> {
        val decryptor = subjectKeys.decryptor(contacts.map { it.subjectId }.distinct())
        return contacts.map { ReadContact(stored = it, contact = decryptor.decryptContact(it, source)) }
    }

    fun emailIndex(email: String?): String? {
        return email?.let { blindIndex.ofCaseInsensitive(it) }
    }

    fun nameIndex(name: String, lastName: String): String? {
        return fullNameIndex("$name $lastName")
    }

    fun phoneIndex(phone: String?): String? {
        val digits = phone?.filter { it in '0'..'9' }.orEmpty()
        return digits.takeIf { it.isNotEmpty() }?.let(blindIndex::of)
    }

    fun indexes(contact: Contact): ContactIndexes {
        return ContactIndexes(
            email = emailIndex(contact.email),
            name = nameIndex(contact.name, contact.lastName),
            phone = phoneIndex(contact.phone),
        )
    }

    fun searchIndexes(query: String): ContactIndexes {
        val looksLikePhone = query.all { it in '0'..'9' || it in PHONE_SEPARATORS }
        return ContactIndexes(
            email = emailIndex(query.trim()),
            name = fullNameIndex(query),
            phone = if (looksLikePhone) phoneIndex(query) else null,
        )
    }

    private fun fullNameIndex(fullName: String): String? {
        val normalized = fullName.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
        return normalized.takeIf { it.isNotEmpty() }?.let(blindIndex::ofCaseInsensitive)
    }

    private fun Encryptor.changes(read: ReadContact, edited: Contact): EncryptedContact {
        val current = read.contact
        val stored = read.stored.encrypted
        return EncryptedContact(
            name = if (edited.name == current.name) stored.name else encryptText(edited.name),
            lastName = if (edited.lastName == current.lastName) stored.lastName else encryptText(edited.lastName),
            phone = changed(stored.phone, current.phone, edited.phone),
            email = changed(stored.email, current.email, edited.email),
            description = changed(stored.description, current.description, edited.description),
        )
    }

    private fun Encryptor.changed(stored: ByteArray?, current: String?, edited: String?): ByteArray? {
        return if (edited == current) stored else edited?.let(::encryptText)
    }

    private fun Decryptor.decryptContact(stored: StoredContact, source: String): Contact {
        val subjectId = stored.subjectId
        val encrypted = stored.encrypted
        val name = decryptText(subjectId, encrypted.name)
        val lastName = decryptText(subjectId, encrypted.lastName)
        val optional = listOf(encrypted.phone, encrypted.email, encrypted.description).map { ciphertext ->
            ciphertext?.let { decryptText(subjectId, it) }
        }
        val (phone, email, description) = optional
        val fields = listOfNotNull(name, lastName, phone, email, description)
        if (fields.any { it is Decrypted.Missing }) missingKeys.report(source)
        return Contact(
            name = name.valueOrNull().orEmpty(),
            lastName = lastName.valueOrNull().orEmpty(),
            phone = phone?.valueOrNull(),
            email = email?.valueOrNull(),
            description = description?.valueOrNull(),
        )
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val PHONE_SEPARATORS = " +-()."
    }
}

internal class ReadContact(val stored: StoredContact, val contact: Contact)

internal class ContactEdit(val read: ReadContact, val edited: Contact)
