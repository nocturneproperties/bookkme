package library.encryption

interface Encryptor {
    fun encrypt(plaintext: String): String

    fun encrypt(plaintext: ByteArray): ByteArray
}

fun Encryptor.encryptText(plaintext: String): ByteArray {
    return encrypt(plaintext.encodeToByteArray())
}

fun Encryptor.encryptTextIfChanged(stored: ByteArray, current: String, edited: String): ByteArray {
    return if (edited == current) stored else encryptText(edited)
}

fun Encryptor.encryptOptionalTextIfChanged(stored: ByteArray?, current: String?, edited: String?): ByteArray? {
    return if (edited == current) stored else edited?.let(::encryptText)
}
