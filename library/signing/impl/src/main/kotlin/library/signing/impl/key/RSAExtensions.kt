package library.signing.impl.key

import com.auth0.jwt.interfaces.RSAKeyProvider
import library.signing.SigningKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

internal fun rsaProviderFrom(signingKey: SigningKey): RSAKeyProvider = object : RSAKeyProvider {
    override fun getPublicKeyById(id: String?): RSAPublicKey {
        return RsaSigningKeyFactory.parsePublicKey(signingKey.publicKeyPem)
    }

    override fun getPrivateKey(): RSAPrivateKey = RsaSigningKeyFactory.parsePrivateKey(signingKey.privateKeyPem)

    override fun getPrivateKeyId(): String = signingKey.id.toString()
}
