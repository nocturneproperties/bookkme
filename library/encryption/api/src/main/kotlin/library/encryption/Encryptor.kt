package library.encryption

interface Encryptor {
    fun encrypt(plaintext: String): String

    fun encrypt(plaintext: ByteArray): ByteArray
}

fun Encryptor.encryptText(plaintext: String): ByteArray {
    return encrypt(plaintext.encodeToByteArray())
}
