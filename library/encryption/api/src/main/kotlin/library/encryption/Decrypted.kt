package library.encryption

sealed interface Decrypted<out T> {
    class Plain<out T>(val value: T) : Decrypted<T>

    data object Shredded : Decrypted<Nothing>

    data object Missing : Decrypted<Nothing>
}

fun <T, R> Decrypted<T>.map(transform: (T) -> R): Decrypted<R> {
    return when (this) {
        is Decrypted.Plain -> Decrypted.Plain(transform(value))
        Decrypted.Shredded -> Decrypted.Shredded
        Decrypted.Missing -> Decrypted.Missing
    }
}

fun <T> Decrypted<T>.valueOrNull(): T? {
    return (this as? Decrypted.Plain)?.value
}
