package library.encryption

fun interface Encryptor {
    fun encrypt(plaintext: String): String
}
