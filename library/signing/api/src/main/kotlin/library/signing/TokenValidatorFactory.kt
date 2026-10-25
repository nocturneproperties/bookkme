package library.signing

interface TokenValidatorFactory {
    fun forType(type: ValidationType): TokenValidator
}