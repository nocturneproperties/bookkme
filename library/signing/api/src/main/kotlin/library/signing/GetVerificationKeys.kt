package library.signing

interface GetVerificationKeys {
    suspend operator fun invoke(): Result<List<SigningKey>>
}
