package library.signing.route

import com.bookk.core.service.di.injectScoped
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import library.signing.GetVerificationKeys
import org.koin.core.qualifier.Qualifier
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.uuid.Uuid

fun Route.jwks(qualifier: Qualifier) {
    /**
     * Summary: JSON Web Key Set
     * Description: Publishes the public keys used to verify tokens issued by this service.
     */
    get("/jwks.json") {
        val getVerificationKeys by application.injectScoped<GetVerificationKeys>(qualifier)
        val keys = getVerificationKeys().getOrThrow().map { createJwk(it.id, it.publicKeyPem) }
        call.respondText(jwksJson.encodeToString(JwkSet(keys)), ContentType.Application.Json)
    }
}

val jwksJson = Json { encodeDefaults = true }

@Serializable
private data class Jwk(
    val kty: String = "RSA",
    val use: String = "sig",
    val alg: String = "RS256",
    val kid: String,
    val n: String,
    val e: String
)

@Serializable
private data class JwkSet(val keys: List<Jwk>)

private fun createJwk(id: Uuid, publicKeyPem: String): Jwk {
    val publicKey = publicKeyPem.toRsaPublicKey()
    return Jwk(
        kid = id.toString(),
        n = publicKey.modulus.toBase64Url(),
        e = publicKey.publicExponent.toBase64Url()
    )
}

private fun String.toRsaPublicKey(): RSAPublicKey {
    val base64 = replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .lines()
        .joinToString("") { it.trim() }
    val spec = X509EncodedKeySpec(Base64.getDecoder().decode(base64))
    return KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
}

private fun BigInteger.toBase64Url(): String {
    val bytes = toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
