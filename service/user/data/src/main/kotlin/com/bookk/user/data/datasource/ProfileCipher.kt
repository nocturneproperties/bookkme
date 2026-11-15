package com.bookk.user.data.datasource

import com.bookk.user.data.orm.entity.ContactIndexes
import com.bookk.user.data.orm.entity.EncryptedProfile
import com.bookk.user.domain.api.entity.User
import library.encryption.BlindIndex
import library.encryption.Decrypted
import library.encryption.Encryptor
import library.encryption.MissingKeys
import library.encryption.SubjectKeys
import library.encryption.decryptText
import library.encryption.encryptText
import library.encryption.ofCaseInsensitive
import library.encryption.valueOrNull
import kotlin.uuid.Uuid

internal class ProfileCipher(
    private val subjectKeys: SubjectKeys,
    private val blindIndex: BlindIndex,
    private val missingKeys: MissingKeys,
) {

    suspend fun encrypt(user: User): EncryptedProfile? {
        val encryptor = subjectKeys.encryptor(user.id) ?: return null
        return EncryptedProfile(
            name = encryptor.encryptText(user.name),
            lastName = encryptor.encryptText(user.lastName),
            email = encryptor.encryptText(user.email),
            phone = user.phone?.let(encryptor::encryptText),
        )
    }

    suspend fun encryptChanges(read: ReadProfile, edited: User): EncryptedProfile? {
        val encryptor = subjectKeys.encryptor(edited.id) ?: return null
        val current = read.user
        val stored = read.stored
        return EncryptedProfile(
            name = encryptor.changed(stored.name, current.name, edited.name),
            lastName = encryptor.changed(stored.lastName, current.lastName, edited.lastName),
            email = encryptor.changed(stored.email, current.email, edited.email),
            phone = if (edited.phone == current.phone) stored.phone else edited.phone?.let(encryptor::encryptText),
        )
    }

    suspend fun decrypt(userId: Uuid, profile: EncryptedProfile): ReadProfile {
        val decryptor = subjectKeys.decryptor(listOf(userId))
        val name = decryptor.decryptText(userId, profile.name)
        val lastName = decryptor.decryptText(userId, profile.lastName)
        val email = decryptor.decryptText(userId, profile.email)
        val phone = profile.phone?.let { decryptor.decryptText(userId, it) }
        val fields = listOfNotNull(name, lastName, email, phone)
        if (fields.any { it is Decrypted.Missing }) missingKeys.report(SOURCE)
        val user = User(
            id = userId,
            name = name.valueOrNull().orEmpty(),
            lastName = lastName.valueOrNull().orEmpty(),
            email = email.valueOrNull().orEmpty(),
            phone = phone?.valueOrNull(),
        )
        return ReadProfile(user = user, stored = profile, isReadable = fields.all { it is Decrypted.Plain })
    }

    fun indexes(user: User): ContactIndexes {
        return ContactIndexes(email = indexOf(user.email), phone = user.phone?.let(::indexOf))
    }

    fun indexOf(contact: String): String {
        return blindIndex.ofCaseInsensitive(contact)
    }

    private fun Encryptor.changed(stored: ByteArray, current: String, edited: String): ByteArray {
        return if (edited == current) stored else encryptText(edited)
    }

    private companion object {
        const val SOURCE = "profile"
    }
}

internal class ReadProfile(val user: User, val stored: EncryptedProfile, val isReadable: Boolean)
