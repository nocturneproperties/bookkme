package library.email

data class VerifiedEmail(
    val email: String,
    val purpose: String,
) {
    companion object {
        fun stub(
            email: String = "client@example.com",
            purpose: String = "purpose",
        ) = VerifiedEmail(email, purpose)
    }
}
