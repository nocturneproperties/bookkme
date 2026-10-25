package library.signing.impl.key

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.RSAKeyProvider
import java.net.URI
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

internal class RemoteRsaKeyProvider(hostname: String) : RSAKeyProvider {

    private val provider = JwkProviderBuilder(URI("http://${hostname}/jwks.json").toURL())
        .cached(10, 1, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    override fun getPublicKeyById(id: String?): RSAPublicKey {
        val keyId = requireNotNull(id) { "Access token is missing its kid header" }
        return provider.get(keyId).publicKey as RSAPublicKey
    }

    override fun getPrivateKey(): RSAPrivateKey? = null

    override fun getPrivateKeyId(): String? = null
}
