package library.encryption

import kotlin.uuid.Uuid

fun interface Decryptor {
    fun decrypt(subjectId: Uuid, ciphertext: String): String?
}
