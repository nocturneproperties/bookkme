package library.signing.impl.key

internal fun interface SigningKeyPairGenerator {
    fun generate(): Pair<String, String>
}
