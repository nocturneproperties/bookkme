package com.bookk.user.data.datasource

import com.bookk.user.data.orm.entity.ContactIndexes
import com.bookk.user.data.orm.entity.EncryptedProfile
import com.bookk.user.domain.api.entity.User
import library.encryption.BlindIndex
import library.encryption.Decrypted
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

    suspend fun decrypt(userId: Uuid, profile: EncryptedProfile): User {
        val decryptor = subjectKeys.decryptor(listOf(userId))
        return when (val email = decryptor.decryptText(userId, profile.email)) {
            is Decrypted.Plain -> User(
                id = userId,
                name = decryptor.decryptText(userId, profile.name).valueOrNull().orEmpty(),
                lastName = decryptor.decryptText(userId, profile.lastName).valueOrNull().orEmpty(),
                email = email.value,
                phone = profile.phone?.let { decryptor.decryptText(userId, it).valueOrNull() },
            )

            Decrypted.Shredded -> unreadable(userId)

            Decrypted.Missing -> unreadable(userId).also { missingKeys.report(SOURCE) }
        }
    }

    fun indexes(user: User): ContactIndexes {
        return ContactIndexes(email = indexOf(user.email), phone = user.phone?.let(::indexOf))
    }

    fun indexOf(contact: String): String {
        return blindIndex.ofCaseInsensitive(contact)
    }

    private fun unreadable(userId: Uuid): User {
        return User(id = userId, name = "", lastName = "", email = "", phone = null)
    }

    private companion object {
        const val SOURCE = "profile"
    }
}
