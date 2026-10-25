@file:OptIn(ExperimentalEncodingApi::class)

package library.signing.impl.key

import java.security.Key
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object RsaSigningKeyFactory {

    private val rsaKeyFactory = KeyFactory.getInstance("RSA")

    fun generate(): Pair<String, String> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        return keyPair.public.toPem("PUBLIC KEY") to keyPair.private.toPem("PRIVATE KEY")
    }

    fun parsePrivateKey(pem: String): RSAPrivateKey {
        val spec = PKCS8EncodedKeySpec(pem.pemToBytes("PRIVATE KEY"))
        return rsaKeyFactory.generatePrivate(spec) as RSAPrivateKey
    }

    fun parsePublicKey(pem: String): RSAPublicKey {
        val spec = X509EncodedKeySpec(pem.pemToBytes("PUBLIC KEY"))
        return rsaKeyFactory.generatePublic(spec) as RSAPublicKey
    }

    private fun Key.toPem(label: String): String {
        val base64 = Base64.encode(encoded)
        return "-----BEGIN $label-----\n$base64\n-----END $label-----"
    }

    private fun String.pemToBytes(label: String): ByteArray {
        return replace("-----BEGIN $label-----", "")
            .replace("-----END $label-----", "")
            .lines()
            .joinToString("") { it.trim() }
            .let { Base64.decode(it) }
    }
}
