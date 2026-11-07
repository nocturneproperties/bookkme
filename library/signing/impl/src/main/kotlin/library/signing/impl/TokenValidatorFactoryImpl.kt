package library.signing.impl

import library.signing.TokenAudience
import library.signing.TokenValidator
import library.signing.TokenValidatorFactory
import library.signing.ValidationType
import library.signing.impl.key.TokenValidation
import java.util.concurrent.ConcurrentHashMap

internal class TokenValidatorFactoryImpl : TokenValidatorFactory {

    private val validationCache = ConcurrentHashMap<ValidationType, TokenValidator>()

    override fun forType(type: ValidationType): TokenValidator = validationCache.getOrPut(type) {
        TokenValidation(issuer = type.issuer, audience = type.audience, remoteProviderHostname = type.issuer)
    }
}

private val ValidationType.issuer: String
    get() = when (this) {
        ValidationType.AUTH_TOKEN, ValidationType.ACTION_CONFIRMATION -> TokenConstants.authServiceHostname
        ValidationType.SERVICE_QUOTE -> TokenConstants.businessServiceHostname
    }

internal val ValidationType.audience: String
    get() = when (this) {
        ValidationType.AUTH_TOKEN -> TokenAudience.access
        ValidationType.SERVICE_QUOTE -> TokenAudience.serviceQuote
        ValidationType.ACTION_CONFIRMATION -> TokenAudience.actionConfirmation
    }
