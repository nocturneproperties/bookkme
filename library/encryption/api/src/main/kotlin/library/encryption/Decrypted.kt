package library.encryption

sealed interface Decrypted<out T> {
    class Plain<out T>(val value: T) : Decrypted<T>

    data object Shredded : Decrypted<Nothing>

    data object Missing : Decrypted<Nothing>
}
