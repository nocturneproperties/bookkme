package library.signing.impl

import com.bookk.core.AppLevelConstants
import library.signing.TokenValidator
import library.signing.TokenValidatorFactory
import library.signing.ValidationType
import library.signing.impl.key.TokenValidation
import java.util.concurrent.ConcurrentHashMap

internal class TokenValidatorFactoryImpl : TokenValidatorFactory {

    private val validationCache = ConcurrentHashMap<ValidationType, TokenValidator>()

    override fun forType(type: ValidationType): TokenValidator {
        return when (type) {
            ValidationType.AUTH_TOKEN -> validationCache.getOrPut(type) {
                TokenValidation(
                    issuer = TokenConstants.authServiceHostname,
                    audience = AppLevelConstants.domainName,
                    remoteProviderHostname = TokenConstants.authServiceHostname
                )
            }
            ValidationType.SERVICE_QUOTE -> validationCache.getOrPut(type) {
                TokenValidation(
                    issuer = TokenConstants.businessServiceHostname,
                    audience = AppLevelConstants.domainName,
                    remoteProviderHostname = TokenConstants.businessServiceHostname
                )
            }
        }
    }
}