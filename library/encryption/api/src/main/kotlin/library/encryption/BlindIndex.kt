package library.encryption

fun interface BlindIndex {
    fun of(value: String): String
}

fun BlindIndex.ofCaseInsensitive(value: String): String {
    return of(value.trim().lowercase())
}
