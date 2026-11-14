package library.encryption

import kotlin.uuid.Uuid

interface Decryptor {
    fun decrypt(subjectId: Uuid, ciphertext: String): Decrypted<String>

    fun decrypt(subjectId: Uuid, ciphertext: ByteArray): Decrypted<ByteArray>
}

fun Decryptor.decryptText(subjectId: Uuid, ciphertext: ByteArray): Decrypted<String> {
    return decrypt(subjectId, ciphertext).map { it.decodeToString() }
}
