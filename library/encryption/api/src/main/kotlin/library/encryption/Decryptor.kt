package library.encryption

import kotlin.uuid.Uuid

interface Decryptor {
    fun decrypt(subjectId: Uuid, ciphertext: String): Decrypted<String>

    fun decrypt(subjectId: Uuid, ciphertext: ByteArray): Decrypted<ByteArray>
}
