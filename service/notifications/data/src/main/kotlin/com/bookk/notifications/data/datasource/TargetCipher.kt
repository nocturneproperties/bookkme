package com.bookk.notifications.data.datasource

import library.encryption.Decrypted
import library.encryption.MissingKeys
import library.encryption.SubjectKeys
import kotlin.uuid.Uuid

internal class TargetCipher(
    private val subjectKeys: SubjectKeys,
    private val missingKeys: MissingKeys,
) {

    suspend fun encrypt(userId: Uuid, target: String): ByteArray? {
        val encryptor = subjectKeys.encryptor(userId) ?: return null
        return encryptor.encrypt(target.encodeToByteArray())
    }

    suspend fun decrypt(userId: Uuid, ciphertext: ByteArray, table: String): String? {
        val decrypted = subjectKeys.decryptor(listOf(userId)).decrypt(userId, ciphertext)
        if (decrypted is Decrypted.Missing) missingKeys.report(table)
        return (decrypted as? Decrypted.Plain)?.value?.decodeToString()
    }
}
