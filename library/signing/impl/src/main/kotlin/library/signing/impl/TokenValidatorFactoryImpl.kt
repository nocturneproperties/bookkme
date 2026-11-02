package library.signing.impl

import library.signing.TokenAudience
import library.signing.TokenValidator
import library.signing.TokenValidatorFactory
import library.signing.ValidationType
import library.signing.impl.key.TokenValidation
import java.util.concurrent.ConcurrentHashMap

internal class TokenValidatorFactoryImpl : TokenValidatorFactory {

    private val validationCache = ConcurrentHashMap<ValidationType, TokenValidator>()

    override fun forType(type: ValidationType): TokenValidator = when (type) {
        ValidationType.AUTH_TOKEN -> validationCache.getOrPut(type) {
            TokenValidation(
                issuer = TokenConstants.authServiceHostname,
                audience = TokenAudience.access,
                remoteProviderHostname = TokenConstants.authServiceHostname,
            )
        }

        ValidationType.SERVICE_QUOTE -> validationCache.getOrPut(type) {
            TokenValidation(
                issuer = TokenConstants.businessServiceHostname,
                audience = TokenAudience.access,
                remoteProviderHostname = TokenConstants.businessServiceHostname,
            )
        }

        ValidationType.STEP_UP -> validationCache.getOrPut(type) {
            TokenValidation(
                issuer = TokenConstants.authServiceHostname,
                audience = TokenAudience.stepUp,
                remoteProviderHostname = TokenConstants.authServiceHostname,
            )
        }
    }
}
