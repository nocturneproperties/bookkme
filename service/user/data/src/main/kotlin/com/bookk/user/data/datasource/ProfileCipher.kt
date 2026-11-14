package com.bookk.user.data.datasource

import com.bookk.user.data.orm.entity.ContactIndexes
import com.bookk.user.data.orm.entity.EncryptedProfile
import com.bookk.user.data.orm.entity.UserEntity
import com.bookk.user.domain.api.entity.User
import library.encryption.BlindIndex
import library.encryption.Decrypted
import library.encryption.Decryptor
import library.encryption.Encryptor
import library.encryption.MissingKeys
import library.encryption.SubjectKeys
import library.encryption.ofCaseInsensitive
import kotlin.uuid.Uuid

internal class ProfileCipher(
    private val subjectKeys: SubjectKeys,
    private val blindIndex: BlindIndex,
    private val missingKeys: MissingKeys,
) {

    suspend fun encrypt(user: User): EncryptedProfile? {
        val encryptor = subjectKeys.encryptor(user.id) ?: return null
        return EncryptedProfile(
            name = encryptor.bytesOf(user.name),
            lastName = encryptor.bytesOf(user.lastName),
            email = encryptor.bytesOf(user.email),
            phone = user.phone?.let { encryptor.bytesOf(it) },
        )
    }

    fun indexes(user: User): ContactIndexes {
        return ContactIndexes(email = index(user.email), phone = user.phone?.let(::index))
    }

    fun index(contact: String): String {
        return blindIndex.ofCaseInsensitive(contact)
    }

    suspend fun decrypt(entity: UserEntity): User {
        val encryptedEmail = entity.emailEncrypted ?: return entity.plaintextDomain()
        val id = entity.id.value
        val decryptor = subjectKeys.decryptor(listOf(id))
        val email = decryptor.decrypt(id, encryptedEmail)
        if (email !is Decrypted.Plain) return unreadable(id, email)
        return User(
            id = id,
            name = decryptor.textOf(id, entity.nameEncrypted).orEmpty(),
            lastName = decryptor.textOf(id, entity.lastNameEncrypted).orEmpty(),
            email = email.value.decodeToString(),
            phone = decryptor.textOf(id, entity.phoneEncrypted),
        )
    }

    private fun unreadable(id: Uuid, outcome: Decrypted<ByteArray>): User {
        if (outcome is Decrypted.Missing) missingKeys.report(SOURCE)
        return User(id = id, name = "", lastName = "", email = "", phone = null)
    }

    private fun Encryptor.bytesOf(text: String): ByteArray {
        return encrypt(text.encodeToByteArray())
    }

    private fun Decryptor.textOf(id: Uuid, ciphertext: ByteArray?): String? {
        val decrypted = ciphertext?.let { decrypt(id, it) }
        return (decrypted as? Decrypted.Plain)?.value?.decodeToString()
    }

    private companion object {
        const val SOURCE = "profile"
    }
}
