package library.signing

interface GetActiveSigningKey {
    suspend operator fun invoke(): Result<SigningKey>
}
