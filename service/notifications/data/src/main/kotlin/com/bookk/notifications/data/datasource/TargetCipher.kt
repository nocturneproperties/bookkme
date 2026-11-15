package com.bookk.notifications.data.datasource

import library.encryption.Decrypted
import library.encryption.MissingKeys
import library.encryption.SubjectKeys
import library.encryption.decryptText
import library.encryption.encryptText
import library.encryption.valueOrNull
import kotlin.uuid.Uuid

internal class TargetCipher(
    private val subjectKeys: SubjectKeys,
    private val missingKeys: MissingKeys,
) {

    suspend fun encrypt(userId: Uuid, target: String): ByteArray? {
        return subjectKeys.encryptor(userId)?.encryptText(target)
    }

    suspend fun decrypt(userId: Uuid, ciphertext: ByteArray, table: String): String? {
        val decrypted = subjectKeys.decryptor(listOf(userId)).decryptText(userId, ciphertext)
        if (decrypted is Decrypted.Missing) missingKeys.report(table)
        return decrypted.valueOrNull()
    }
}
