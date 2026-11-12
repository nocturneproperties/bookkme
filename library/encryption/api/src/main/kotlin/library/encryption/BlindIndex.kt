package library.encryption

fun interface BlindIndex {
    fun of(value: String): String
}
